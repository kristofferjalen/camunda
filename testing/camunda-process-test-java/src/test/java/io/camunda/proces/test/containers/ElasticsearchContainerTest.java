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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

public class ElasticsearchContainerTest {

  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("process").startEvent().endEvent().done();

  @Test
  void shouldExportToElasticsearch() {
    // given
    final CamundaContainerRuntime runtime = new CamundaContainerRuntime();
    runtime.start();

    // when
    final ZeebeClient zeebeClient = createZeebeClient(runtime);
    zeebeClient.newDeployResourceCommand().addProcessModel(PROCESS, "process.bpmn").send().join();

    // then
    final String elasticsearchAddress = runtime.getElasticsearchContainer().getHttpHostAddress();
    final URI elasticsearchIndexStatsEndpoint =
        URI.create("http://" + elasticsearchAddress + "/_stats");

    Awaitility.await()
        .untilAsserted(
            () -> {
              final String responseBody = sendGetRequest(elasticsearchIndexStatsEndpoint);
              assertThat(responseBody).contains("zeebe-record_deployment");
            });
  }

  private static ZeebeClient createZeebeClient(final CamundaContainerRuntime runtime) {
    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();
    final URI grpcAddress = URI.create("http://0.0.0.0:" + zeebeContainer.getGrpcApiPort());
    final URI restAddress = URI.create("http://0.0.0.0:" + zeebeContainer.getRestApiPort());

    return ZeebeClient.newClientBuilder()
        .usePlaintext()
        .grpcAddress(grpcAddress)
        .restAddress(restAddress)
        .build();
  }

  private static String sendGetRequest(final URI uri) throws IOException {
    try (final CloseableHttpClient httpClient = HttpClients.createDefault()) {
      return httpClient.execute(
          new HttpGet(uri),
          response -> {
            assertThat(response.getCode()).isEqualTo(200);
            return EntityUtils.toString(response.getEntity());
          });
    }
  }
}
