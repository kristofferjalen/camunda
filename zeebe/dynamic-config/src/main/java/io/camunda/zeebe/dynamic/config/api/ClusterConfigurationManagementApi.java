/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.AddMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterDisableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExporterEnableRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.JoinPartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.LeavePartitionRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ReassignPartitionsRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.RemoveMembersRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ScaleRequest;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.future.ActorFuture;

/** Defines the API for the configuration management requests. */
public interface ClusterConfigurationManagementApi {

  ActorFuture<ClusterConfigurationChangeResponse> addMembers(AddMembersRequest addMembersRequest);

  ActorFuture<ClusterConfigurationChangeResponse> removeMembers(
      RemoveMembersRequest removeMembersRequest);

  ActorFuture<ClusterConfigurationChangeResponse> joinPartition(
      JoinPartitionRequest joinPartitionRequest);

  ActorFuture<ClusterConfigurationChangeResponse> leavePartition(
      LeavePartitionRequest leavePartitionRequest);

  ActorFuture<ClusterConfigurationChangeResponse> reassignPartitions(
      ReassignPartitionsRequest reassignPartitionsRequest);

  ActorFuture<ClusterConfigurationChangeResponse> scaleMembers(ScaleRequest scaleRequest);

  /**
   * Forces a scale down of the cluster. The members that are not specified in the request will be
   * removed forcefully. The replicas of partitions on the removed members won't be re-assigned. As
   * a result the number of replicas for those partitions will be reduced.
   *
   * <p>This is expected to be used to force remove a set of brokers when they are unreachable.
   */
  ActorFuture<ClusterConfigurationChangeResponse> forceScaleDown(
      ScaleRequest forceScaleDownRequest);

  ActorFuture<ClusterConfigurationChangeResponse> disableExporter(
      ExporterDisableRequest exporterDisableRequest);

  ActorFuture<ClusterConfigurationChangeResponse> enableExporter(
      ExporterEnableRequest enableRequest);

  ActorFuture<ClusterConfiguration> cancelTopologyChange(
      ClusterConfigurationManagementRequest.CancelChangeRequest cancelChangeRequest);

  ActorFuture<ClusterConfiguration> getTopology();
}
