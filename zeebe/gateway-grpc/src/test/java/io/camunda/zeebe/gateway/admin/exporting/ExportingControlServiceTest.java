/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.admin.exporting;

import static io.camunda.zeebe.gateway.admin.exporting.ExportingControlServiceTest.RequestMatcher.requestTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.api.dto.BrokerRequest;
import io.camunda.zeebe.broker.client.impl.BrokerClusterStateImpl;
import io.camunda.zeebe.gateway.admin.IncompleteTopologyException;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatcher;

public class ExportingControlServiceTest {

  @ParameterizedTest
  @MethodSource("validTopologies")
  void shouldPauseOnAllBrokersAndPartitions(final BrokerClusterState topology) {
    // given
    final var client = setupBrokerClient(topology);
    final var service = new ExportingControlService(client);

    // when
    service.pauseExporting().join();

    // then
    for (final var partition : topology.getPartitions()) {
      for (final var follower :
          Optional.ofNullable(topology.getFollowersForPartition(partition)).orElse(Set.of())) {
        verify(client).sendRequest(requestTo(partition, follower));
      }
      verify(client).sendRequest(requestTo(partition, topology.getLeaderForPartition(partition)));

      for (final var inactive :
          Optional.ofNullable(topology.getInactiveNodesForPartition(partition)).orElse(Set.of())) {
        verify(client).sendRequest(requestTo(partition, inactive));
      }
    }
  }

  @ParameterizedTest
  @MethodSource("invalidTopologies")
  void shouldFailOnIncompleteTopology(final BrokerClusterState topology) {
    // given
    final var client = setupBrokerClient(topology);
    final var service = new ExportingControlService(client);

    // then
    assertThatExceptionOfType(IncompleteTopologyException.class)
        .isThrownBy(service::pauseExporting);
  }

  @ParameterizedTest
  @MethodSource("validTopologies")
  void shouldSucceedIfAllRequestsFinish(final BrokerClusterState topology) {
    // given
    final var client = setupBrokerClient(topology);
    final var service = new ExportingControlService(client);

    // then
    assertThat(service.pauseExporting()).succeedsWithin(Duration.ofSeconds(10));
  }

  @ParameterizedTest
  @MethodSource("validTopologies")
  void shouldFailIfAnyRequestFails(final BrokerClusterState topology) {
    // given
    final var client = setupBrokerClient(topology);
    final var service = new ExportingControlService(client);

    // when
    when(client.sendRequest(requestTo(1, 1))).thenThrow(new RuntimeException("request failed"));

    // then
    assertThatExceptionOfType(Throwable.class).isThrownBy(service::pauseExporting);
  }

  private BrokerClient setupBrokerClient(final BrokerClusterState topology) {
    final var client = mock(BrokerClient.class);
    final var topologyManager = mock(BrokerTopologyManager.class);

    when(topologyManager.getTopology()).thenReturn(topology);
    when(client.getTopologyManager()).thenReturn(topologyManager);
    when(client.sendRequest(any())).thenReturn(CompletableFuture.completedFuture(null));
    return client;
  }

  public static Stream<Arguments> validTopologies() {
    return Stream.of(
        arguments(
            named(
                "Evenly distributed",
                ofTopology(Map.of(1, List.of(1, 2, 3), 2, List.of(2, 1, 3), 3, List.of(3, 1, 2))))),
        arguments(
            named(
                "Single broker, no replication",
                ofTopology(Map.of(1, List.of(1), 2, List.of(1), 3, List.of(1))))),
        arguments(
            named(
                "Multiple brokers, no replication",
                ofTopology(Map.of(1, List.of(1), 2, List.of(2), 3, List.of(3))))));
  }

  public static Stream<Arguments> invalidTopologies() {
    return Stream.of(
        arguments(named("Partition without members", ofTopology(3, 1, 3, Map.of(1, List.of())))),
        arguments(
            named("Partition with missing member", ofTopology(3, 1, 3, Map.of(1, List.of(1, 2))))),
        arguments(named("Empty topology", ofTopology(1, 1, 1, Map.of()))));
  }

  private static BrokerClusterState ofTopology(
      final int clusterSize,
      final int partitionCount,
      final int replicationFactor,
      final Map<Integer, List<Integer>> topology) {
    final var state = new BrokerClusterStateImpl();
    final var brokers =
        topology.values().stream().flatMap(Collection::stream).collect(Collectors.toSet());

    state.setClusterSize(clusterSize);
    state.setPartitionsCount(partitionCount);
    state.setReplicationFactor(replicationFactor);

    brokers.forEach(state::addBrokerIfAbsent);
    topology.keySet().forEach(state::addPartitionIfAbsent);

    for (final var entry : topology.entrySet()) {
      final var partition = entry.getKey();
      final var members = entry.getValue();

      if (brokers.size() != 0) {
        Optional.ofNullable(members.get(0))
            .ifPresent(leader -> state.setPartitionLeader(partition, leader, 10));
        members.stream()
            .skip(1)
            .forEach(follower -> state.addPartitionFollower(partition, follower));
      }

      brokers.stream()
          .filter(broker -> !members.contains(broker))
          .forEach(inactive -> state.addPartitionInactive(partition, inactive));
    }

    return state;
  }

  private static BrokerClusterState ofTopology(final Map<Integer, List<Integer>> topology) {
    final var brokers = topology.values().stream().flatMap(Collection::stream).distinct().count();
    final var partitions = topology.size();
    final var replicationFactor =
        topology.values().stream()
            .map(Collection::size)
            .max(Comparator.comparingInt((x) -> x))
            .orElseThrow();
    return ofTopology((int) brokers, partitions, replicationFactor, topology);
  }

  record RequestMatcher(int partitionId, int brokerId)
      implements ArgumentMatcher<BrokerRequest<Void>> {

    static BrokerRequest<Void> requestTo(final int partitionId, final int brokerId) {
      return argThat(new RequestMatcher(partitionId, brokerId));
    }

    @Override
    public boolean matches(final BrokerRequest<Void> argument) {
      return argument.getPartitionId() == partitionId
          && argument.getBrokerId().orElseThrow() == brokerId;
    }
  }
}
