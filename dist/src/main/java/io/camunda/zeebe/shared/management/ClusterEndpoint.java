/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.CancelChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ScaleRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.MemberRemoveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionForceReconfigureOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionLeaveOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation.PartitionChangeOperation.PartitionReconfigurePriorityOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.MemberState;
import io.camunda.zeebe.dynamic.config.state.PartitionState.State;
import io.camunda.zeebe.management.cluster.BrokerState;
import io.camunda.zeebe.management.cluster.BrokerStateCode;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.GetTopologyResponse;
import io.camunda.zeebe.management.cluster.Operation;
import io.camunda.zeebe.management.cluster.Operation.OperationEnum;
import io.camunda.zeebe.management.cluster.PartitionState;
import io.camunda.zeebe.management.cluster.PartitionStateCode;
import io.camunda.zeebe.management.cluster.PlannedOperationsResponse;
import io.camunda.zeebe.management.cluster.TopologyChange;
import io.camunda.zeebe.management.cluster.TopologyChange.StatusEnum;
import io.camunda.zeebe.management.cluster.TopologyChangeCompletedInner;
import io.camunda.zeebe.util.Either;
import java.net.ConnectException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@RestControllerEndpoint(id = "cluster")
public class ClusterEndpoint {
  private static final OffsetDateTime MIN_PARSER_COMPLIANT_DATE =
      OffsetDateTime.parse("0000-01-01T00:00:00Z");
  private final ClusterConfigurationManagementRequestSender requestSender;

  @Autowired
  public ClusterEndpoint(final ClusterConfigurationManagementRequestSender requestSender) {
    this.requestSender = requestSender;
  }

  @GetMapping(produces = "application/json")
  public ResponseEntity<?> clusterTopology() {
    try {
      return mapClusterTopologyResponse(requestSender.getTopology().join());
    } catch (final Exception error) {
      return mapError(error);
    }
  }

  private ResponseEntity<Error> mapError(final Throwable error) {
    if (error instanceof CompletionException) {
      return mapError(error.getCause());
    }

    final var errorResponse = new Error();
    errorResponse.setMessage(error.getMessage());
    final int status =
        switch (error) {
          case final ConnectException ignore -> 502;
          case final NoSuchMemberException ignore -> 502;
          case final TimeoutException ignore -> 504;
          default -> 500;
        };
    return ResponseEntity.status(status).body(errorResponse);
  }

  private ResponseEntity<Error> invalidRequest(final String message) {
    final var errorResponse = new Error();
    errorResponse.setMessage(message);
    return ResponseEntity.status(400).body(errorResponse);
  }

  @PostMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> add(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int id,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          mapOperationResponse(
              requestSender
                  .addMembers(
                      new AddMembersRequest(Set.of(new MemberId(String.valueOf(id))), dryRun))
                  .join());
      case partitions -> ResponseEntity.status(501).body("Adding partitions is not supported");
      case changes ->
          ResponseEntity.status(501)
              .body(
                  "Changing cluster directly is not supported. Use POST /cluster/brokers for scaling the cluster");
    };
  }

  @DeleteMapping(path = "/{resource}/{id}")
  public ResponseEntity<?> remove(
      @PathVariable("resource") final Resource resource,
      @PathVariable final String id,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          mapOperationResponse(
              requestSender
                  .removeMembers(new RemoveMembersRequest(Set.of(new MemberId(id)), dryRun))
                  .join());
      case partitions -> ResponseEntity.status(501).body("Removing partitions is not supported");
      case changes -> {
        if (dryRun) {
          yield ResponseEntity.status(501).body("Dry run is not supported for cancelling changes");
        } else {
          yield cancelChange(id);
        }
      }
    };
  }

  /**
   * Cancels a change with the given id. This is a dangerous operation and should only be used when
   * the change is stuck and cannot make progress on its own. Cancelling a change will not revert
   * already applied operations, so the cluster will be in an intermediate state with partially
   * applied changes. For example, a partition might have been added to a broker, but not removed
   * from another one; so it has a higher number of replicas than the configured value. In another
   * case, the configuration in raft might differ from what is reflected in the ClusterTopology. So
   * a manual intervention would be required to clean up the state.
   */
  private ResponseEntity<?> cancelChange(final String changeId) {
    try {
      return mapClusterTopologyResponse(
          requestSender
              .cancelTopologyChange(new CancelChangeRequest(Long.parseLong(changeId)))
              .join());
    } catch (final NumberFormatException ignore) {
      return invalidRequest("Change id must be a number");
    } catch (final Exception error) {
      return mapError(error);
    }
  }

  @PostMapping(path = "/{resource}", consumes = "application/json")
  public ResponseEntity<?> scale(
      @PathVariable("resource") final Resource resource,
      @RequestBody final List<Integer> ids,
      @RequestParam(defaultValue = "false") final boolean dryRun,
      @RequestParam(defaultValue = "false") final boolean force,
      @RequestParam final Optional<Integer> replicationFactor) {
    return switch (resource) {
      case brokers -> scaleBrokers(ids, dryRun, force, replicationFactor);
      case partitions ->
          new ResponseEntity<>("Scaling partitions is not supported", HttpStatusCode.valueOf(501));
      case changes ->
          ResponseEntity.status(501)
              .body(
                  "Changing cluster directly is not supported. Use POST /cluster/brokers for scaling the cluster");
    };
  }

  private ResponseEntity<?> scaleBrokers(
      final List<Integer> ids,
      final boolean dryRun,
      final boolean force,
      final Optional<Integer> replicationFactor) {
    try {
      final ScaleRequest scaleRequest =
          new ScaleRequest(
              ids.stream().map(String::valueOf).map(MemberId::from).collect(Collectors.toSet()),
              replicationFactor,
              dryRun);
      // here we assume, if it force request it is always force scale down. The coordinator will
      // reject the request if that is not the case.
      final var response =
          force
              ? requestSender.forceScaleDown(scaleRequest).join()
              : requestSender.scaleMembers(scaleRequest).join();
      return mapOperationResponse(response);
    } catch (final Exception error) {
      return mapError(error);
    }
  }

  @PostMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> addSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId,
      @RequestBody final PartitionAddRequest request,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    final int priority = request.priority();
    return switch (resource) {
      case brokers ->
          switch (subResource) {
              // POST /cluster/brokers/1/partitions/2
            case partitions ->
                mapOperationResponse(
                    requestSender
                        .joinPartition(
                            new JoinPartitionRequest(
                                MemberId.from(String.valueOf(resourceId)),
                                subResourceId,
                                priority,
                                dryRun))
                        .join());
            case brokers, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case partitions ->
          switch (subResource) {
              // POST /cluster/partitions/2/brokers/1
            case brokers ->
                mapOperationResponse(
                    requestSender
                        .joinPartition(
                            new JoinPartitionRequest(
                                MemberId.from(String.valueOf(subResourceId)),
                                resourceId,
                                priority,
                                dryRun))
                        .join());
            case partitions, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
    };
  }

  @DeleteMapping(
      path = "/{resource}/{resourceId}/{subResource}/{subResourceId}",
      consumes = "application/json")
  public ResponseEntity<?> removeSubResource(
      @PathVariable("resource") final Resource resource,
      @PathVariable final int resourceId,
      @PathVariable("subResource") final Resource subResource,
      @PathVariable final int subResourceId,
      @RequestParam(defaultValue = "false") final boolean dryRun) {
    return switch (resource) {
      case brokers ->
          switch (subResource) {
            case partitions ->
                mapOperationResponse(
                    requestSender
                        .leavePartition(
                            new LeavePartitionRequest(
                                MemberId.from(String.valueOf(resourceId)), subResourceId, dryRun))
                        .join());
            case brokers, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case partitions ->
          switch (subResource) {
            case brokers ->
                mapOperationResponse(
                    requestSender
                        .leavePartition(
                            new LeavePartitionRequest(
                                MemberId.from(String.valueOf(subResourceId)), resourceId, dryRun))
                        .join());
            case partitions, changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
          };
      case changes -> new ResponseEntity<>(HttpStatusCode.valueOf(404));
    };
  }

  private ResponseEntity<?> mapOperationResponse(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> response) {
    if (response.isRight()) {
      return ResponseEntity.status(202).body(mapResponseType(response.get()));
    } else {
      return mapErrorResponse(response.getLeft());
    }
  }

  private ResponseEntity<?> mapClusterTopologyResponse(
      final Either<ErrorResponse, ClusterConfiguration> response) {
    if (response.isRight()) {
      return ResponseEntity.status(200).body(mapClusterTopology(response.get()));
    } else {
      return mapErrorResponse(response.getLeft());
    }
  }

  private ResponseEntity<Error> mapErrorResponse(final ErrorResponse response) {
    final var errorCode =
        switch (response.code()) {
          case INVALID_REQUEST, OPERATION_NOT_ALLOWED -> 400;
          case CONCURRENT_MODIFICATION -> 409;
          case INTERNAL_ERROR -> 500;
        };
    final var error = new Error();
    error.setMessage(response.message());
    return ResponseEntity.status(errorCode).body(error);
  }

  private static PlannedOperationsResponse mapResponseType(
      final ClusterConfigurationChangeResponse response) {
    return new PlannedOperationsResponse()
        .changeId(response.changeId())
        .currentTopology(mapBrokerStates(response.currentConfiguration()))
        .expectedTopology(mapBrokerStates(response.expectedConfiguration()))
        .plannedChanges(mapOperations(response.plannedChanges()));
  }

  private static List<Operation> mapOperations(
      final List<ClusterConfigurationChangeOperation> operations) {
    return operations.stream().map(ClusterEndpoint::mapOperation).toList();
  }

  private static Operation mapOperation(final ClusterConfigurationChangeOperation operation) {
    return switch (operation) {
      case final MemberJoinOperation join ->
          new Operation()
              .operation(OperationEnum.BROKER_ADD)
              .brokerId(Integer.parseInt(join.memberId().id()));
      case final MemberLeaveOperation leave ->
          new Operation()
              .operation(OperationEnum.BROKER_REMOVE)
              .brokerId(Integer.parseInt(leave.memberId().id()));
      case final PartitionJoinOperation join ->
          new Operation()
              .operation(OperationEnum.PARTITION_JOIN)
              .brokerId(Integer.parseInt(join.memberId().id()))
              .partitionId(join.partitionId())
              .priority(join.priority());
      case final PartitionLeaveOperation leave ->
          new Operation()
              .operation(OperationEnum.PARTITION_LEAVE)
              .brokerId(Integer.parseInt(leave.memberId().id()))
              .partitionId(leave.partitionId());
      case final PartitionReconfigurePriorityOperation reconfigure ->
          new Operation()
              .operation(OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
              .brokerId(Integer.parseInt(reconfigure.memberId().id()))
              .partitionId(reconfigure.partitionId())
              .priority(reconfigure.priority());
      case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
          new Operation()
              .operation(OperationEnum.PARTITION_FORCE_RECONFIGURE)
              .brokerId(Integer.parseInt(partitionForceReconfigureOperation.memberId().id()))
              .partitionId(partitionForceReconfigureOperation.partitionId())
              .brokers(
                  partitionForceReconfigureOperation.members().stream()
                      .map(MemberId::id)
                      .map(Integer::parseInt)
                      .collect(Collectors.toList()));
      case final ClusterConfigurationChangeOperation.MemberRemoveOperation memberRemoveOperation ->
          new Operation()
              .operation(OperationEnum.BROKER_REMOVE)
              .brokerId(Integer.parseInt(memberRemoveOperation.memberId().id()))
              .brokers(List.of(Integer.parseInt(memberRemoveOperation.memberToRemove().id())));
      default -> new Operation().operation(OperationEnum.UNKNOWN);
    };
  }

  private static List<BrokerState> mapBrokerStates(final Map<MemberId, MemberState> topology) {
    return topology.entrySet().stream()
        .map(
            entry ->
                new BrokerState()
                    .id(Integer.parseInt(entry.getKey().id()))
                    .state(mapBrokerState(entry.getValue().state()))
                    .lastUpdatedAt(mapInstantToDateTime(entry.getValue().lastUpdated()))
                    .version(entry.getValue().version())
                    .partitions(mapPartitionStates(entry.getValue().partitions())))
        .toList();
  }

  private static OffsetDateTime mapInstantToDateTime(final Instant timestamp) {
    // Instant.MIN ("-1000000000-01-01T00:00Z") is not compliant with rfc3339 parsers
    // as year field has is not 4 digits, so we replace here with the min possible.
    // see: https://github.com/camunda/camunda/issues/16256
    return timestamp.equals(Instant.MIN)
        ? MIN_PARSER_COMPLIANT_DATE
        : timestamp.atOffset(ZoneOffset.UTC);
  }

  private static BrokerStateCode mapBrokerState(final MemberState.State state) {
    return switch (state) {
      case ACTIVE -> BrokerStateCode.ACTIVE;
      case JOINING -> BrokerStateCode.JOINING;
      case LEAVING -> BrokerStateCode.LEAVING;
      case LEFT -> BrokerStateCode.LEFT;
      case UNINITIALIZED -> BrokerStateCode.UNKNOWN;
    };
  }

  private static List<PartitionState> mapPartitionStates(
      final Map<Integer, io.camunda.zeebe.dynamic.config.state.PartitionState> partitions) {
    return partitions.entrySet().stream()
        .map(
            entry ->
                new PartitionState()
                    .id(entry.getKey())
                    .priority(entry.getValue().priority())
                    .state(mapPartitionState(entry.getValue().state())))
        .toList();
  }

  private static PartitionStateCode mapPartitionState(final State state) {
    return switch (state) {
      case JOINING -> PartitionStateCode.JOINING;
      case ACTIVE -> PartitionStateCode.ACTIVE;
      case LEAVING -> PartitionStateCode.LEAVING;
      case UNKNOWN -> PartitionStateCode.UNKNOWN;
    };
  }

  private static GetTopologyResponse mapClusterTopology(final ClusterConfiguration topology) {
    final var response = new GetTopologyResponse();
    final List<BrokerState> brokers = mapBrokerStates(topology.members());

    response.version(topology.version()).brokers(brokers);
    topology.lastChange().ifPresent(change -> response.lastChange(mapCompletedChange(change)));
    topology.pendingChanges().ifPresent(change -> response.pendingChange(mapOngoingChange(change)));

    return response;
  }

  private static io.camunda.zeebe.management.cluster.CompletedChange mapCompletedChange(
      final CompletedChange completedChange) {
    return new io.camunda.zeebe.management.cluster.CompletedChange()
        .id(completedChange.id())
        .status(mapCompletedChangeStatus(completedChange.status()))
        .startedAt(mapInstantToDateTime(completedChange.startedAt()))
        .completedAt(mapInstantToDateTime(completedChange.completedAt()));
  }

  private static TopologyChange mapOngoingChange(final ClusterChangePlan clusterChangePlan) {
    return new TopologyChange()
        .id(clusterChangePlan.id())
        .status(mapChangeStatus(clusterChangePlan.status()))
        .pending(mapOperations(clusterChangePlan.pendingOperations()))
        .completed(mapCompletedOperations(clusterChangePlan.completedOperations()));
  }

  private static io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum
      mapCompletedChangeStatus(final Status status) {
    return switch (status) {
      case COMPLETED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.COMPLETED;
      case FAILED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.FAILED;
      case CANCELLED -> io.camunda.zeebe.management.cluster.CompletedChange.StatusEnum.CANCELLED;
      case IN_PROGRESS -> throw new IllegalStateException("Completed change cannot be in progress");
    };
  }

  private static StatusEnum mapChangeStatus(final Status status) {
    return switch (status) {
      case IN_PROGRESS -> StatusEnum.IN_PROGRESS;
      case COMPLETED -> StatusEnum.COMPLETED;
      case FAILED -> StatusEnum.FAILED;
      case CANCELLED -> StatusEnum.CANCELLED;
    };
  }

  private static List<TopologyChangeCompletedInner> mapCompletedOperations(
      final List<CompletedOperation> completedOperations) {
    return completedOperations.stream().map(ClusterEndpoint::mapCompletedOperation).toList();
  }

  private static TopologyChangeCompletedInner mapCompletedOperation(
      final CompletedOperation operation) {
    final var mappedOperation =
        switch (operation.operation()) {
          case final MemberJoinOperation join ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_ADD)
                  .brokerId(Integer.parseInt(join.memberId().id()));
          case final MemberLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(Integer.parseInt(leave.memberId().id()));
          case final PartitionJoinOperation join ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_JOIN)
                  .brokerId(Integer.parseInt(join.memberId().id()))
                  .partitionId(join.partitionId())
                  .priority(join.priority());
          case final PartitionLeaveOperation leave ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_LEAVE)
                  .brokerId(Integer.parseInt(leave.memberId().id()))
                  .partitionId(leave.partitionId());
          case final PartitionReconfigurePriorityOperation reconfigure ->
              new TopologyChangeCompletedInner()
                  .operation(
                      TopologyChangeCompletedInner.OperationEnum.PARTITION_RECONFIGURE_PRIORITY)
                  .brokerId(Integer.parseInt(reconfigure.memberId().id()))
                  .partitionId(reconfigure.partitionId())
                  .priority(reconfigure.priority());
          case final PartitionForceReconfigureOperation partitionForceReconfigureOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.PARTITION_FORCE_RECONFIGURE)
                  .brokerId(Integer.parseInt(partitionForceReconfigureOperation.memberId().id()))
                  .partitionId(partitionForceReconfigureOperation.partitionId())
                  .brokers(
                      partitionForceReconfigureOperation.members().stream()
                          .map(MemberId::id)
                          .map(Integer::parseInt)
                          .toList());
          case final MemberRemoveOperation memberRemoveOperation ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.BROKER_REMOVE)
                  .brokerId(Integer.parseInt(memberRemoveOperation.memberId().id()))
                  .brokers(List.of(Integer.parseInt(memberRemoveOperation.memberToRemove().id())));
          default ->
              new TopologyChangeCompletedInner()
                  .operation(TopologyChangeCompletedInner.OperationEnum.UNKNOWN);
        };

    mappedOperation.completedAt(mapInstantToDateTime(operation.completedAt()));

    return mappedOperation;
  }

  public record PartitionAddRequest(int priority) {}

  public enum Resource {
    brokers,
    partitions,
    changes
  }
}
