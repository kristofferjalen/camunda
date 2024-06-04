/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.process;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCancelProcessInstanceRequest;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;

public final class CancelProcessInstanceStub
    implements RequestStub<
        BrokerCancelProcessInstanceRequest, BrokerResponse<ProcessInstanceRecord>> {

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerCancelProcessInstanceRequest.class, this);
  }

  @Override
  public BrokerResponse<ProcessInstanceRecord> handle(
      final BrokerCancelProcessInstanceRequest request) throws Exception {
    return new BrokerResponse<>(
        new ProcessInstanceRecord(), request.getPartitionId(), request.getKey());
  }
}
