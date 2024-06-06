/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.proces.test.containers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.process.test.impl.containers.CamundaContainerRuntime;
import io.camunda.process.test.impl.containers.ZeebeContainer;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.response.BrokerInfo;
import io.camunda.zeebe.client.api.response.PartitionBrokerHealth;
import io.camunda.zeebe.client.api.response.PartitionInfo;
import io.camunda.zeebe.client.api.response.Topology;
import java.net.URI;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

public class ZeebeContainerTest {

  @Test
  void shouldConnectWithZeebeClient() {
    // given
    final CamundaContainerRuntime runtime = new CamundaContainerRuntime();
    runtime.start();

    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();
    final URI grpcAddress = URI.create("http://0.0.0.0:" + zeebeContainer.getGrpcApiPort());
    final URI restAddress = URI.create("http://0.0.0.0:" + zeebeContainer.getRestApiPort());

    // when
    final ZeebeClient zeebeClient =
        ZeebeClient.newClientBuilder()
            .usePlaintext()
            .grpcAddress(grpcAddress)
            .restAddress(restAddress)
            .build();

    // then
    Awaitility.await()
        .untilAsserted(
            () -> {
              final Topology topology = zeebeClient.newTopologyRequest().send().join();

              assertThat(topology.getClusterSize()).isEqualTo(1);
              assertThat(topology.getPartitionsCount()).isEqualTo(1);

              assertThat(topology.getBrokers())
                  .flatExtracting(BrokerInfo::getPartitions)
                  .extracting(PartitionInfo::getHealth)
                  .containsOnly(PartitionBrokerHealth.HEALTHY);
            });
  }
}
