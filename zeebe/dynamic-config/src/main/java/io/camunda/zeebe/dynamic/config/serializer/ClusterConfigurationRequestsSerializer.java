/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.serializer;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.util.Either;

public interface ClusterConfigurationRequestsSerializer {

  byte[] encodeAddMembersRequest(ClusterConfigurationManagementRequest.AddMembersRequest req);

  byte[] encodeRemoveMembersRequest(ClusterConfigurationManagementRequest.RemoveMembersRequest req);

  byte[] encodeJoinPartitionRequest(ClusterConfigurationManagementRequest.JoinPartitionRequest req);

  byte[] encodeLeavePartitionRequest(
      ClusterConfigurationManagementRequest.LeavePartitionRequest req);

  byte[] encodeReassignPartitionsRequest(
      ClusterConfigurationManagementRequest.ReassignPartitionsRequest reassignPartitionsRequest);

  byte[] encodeScaleRequest(ClusterConfigurationManagementRequest.ScaleRequest scaleRequest);

  byte[] encodeCancelChangeRequest(
      ClusterConfigurationManagementRequest.CancelChangeRequest cancelChangeRequest);

  byte[] encodeExporterDisableRequest(
      ClusterConfigurationManagementRequest.ExporterDisableRequest exporterDisableRequest);

  byte[] encodeExporterEnableRequest(
      ClusterConfigurationManagementRequest.ExporterEnableRequest exporterEnableRequest);

  ClusterConfigurationManagementRequest.AddMembersRequest decodeAddMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.RemoveMembersRequest decodeRemoveMembersRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.JoinPartitionRequest decodeJoinPartitionRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.LeavePartitionRequest decodeLeavePartitionRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.ReassignPartitionsRequest decodeReassignPartitionsRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.ScaleRequest decodeScaleRequest(byte[] encodedState);

  ClusterConfigurationManagementRequest.CancelChangeRequest decodeCancelChangeRequest(
      byte[] encodedState);

  ClusterConfigurationManagementRequest.ExporterDisableRequest decodeExporterDisableRequest(
      byte[] encodedRequest);

  ClusterConfigurationManagementRequest.ExporterEnableRequest decodeExporterEnableRequest(
      byte[] encodedRequest);

  byte[] encodeResponse(ClusterConfigurationChangeResponse response);

  byte[] encodeResponse(ClusterConfiguration response);

  byte[] encodeResponse(ErrorResponse response);

  Either<ErrorResponse, ClusterConfigurationChangeResponse> decodeTopologyChangeResponse(
      byte[] encodedResponse);

  Either<ErrorResponse, ClusterConfiguration> decodeClusterTopologyResponse(byte[] encodedResponse);
}
