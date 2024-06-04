/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient.RequestStub;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeployResourceRequest;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.DirectBuffer;

public final class DeployProcessStub
    implements RequestStub<BrokerDeployResourceRequest, BrokerResponse<DeploymentRecord>> {

  private static final long KEY = 123;
  private static final long PROCESS_KEY = 456;
  private static final int PROCESS_VERSION = 789;
  private static final DirectBuffer CHECKSUM = wrapString("checksum");

  @Override
  public void registerWith(final StubbedBrokerClient gateway) {
    gateway.registerHandler(BrokerDeployResourceRequest.class, this);
  }

  protected long getKey() {
    return KEY;
  }

  protected long getProcessDefinitionKey() {
    return PROCESS_KEY;
  }

  public int getProcessVersion() {
    return PROCESS_VERSION;
  }

  @Override
  public BrokerResponse<DeploymentRecord> handle(final BrokerDeployResourceRequest request)
      throws Exception {
    final DeploymentRecord deploymentRecord = request.getRequestWriter();
    deploymentRecord
        .resources()
        .iterator()
        .forEachRemaining(
            r -> {
              deploymentRecord
                  .processesMetadata()
                  .add()
                  .setBpmnProcessId(r.getResourceNameBuffer())
                  .setResourceName(r.getResourceNameBuffer())
                  .setVersion(PROCESS_VERSION)
                  .setKey(PROCESS_KEY)
                  .setChecksum(CHECKSUM)
                  .setTenantId(deploymentRecord.getTenantId());
            });
    return new BrokerResponse<>(deploymentRecord, 0, KEY);
  }
}
