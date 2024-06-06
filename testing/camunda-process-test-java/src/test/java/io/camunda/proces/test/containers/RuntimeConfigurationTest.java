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
import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

public class RuntimeConfigurationTest {

  @Test
  void shouldUseDefaults() {
    // given/when
    final CamundaContainerRuntime runtime = new CamundaContainerRuntime();

    // then
    assertThat(runtime.getZeebeContainer().getDockerImageName()).isEqualTo("camunda/zeebe:latest");
  }

  @Test
  void shouldConfigureZeebeContainer() {
    // given
    final String zeebeDockerImageName =
        "ghcr.io/camunda-community-hub/zeebe-with-hazelcast-exporter";
    final String zeebeDockerImageVersion = "8.5.1";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withZeebeDockerImageName(zeebeDockerImageName)
            .withZeebeDockerImageVersion(zeebeDockerImageVersion)
            .withZeebeEnv("zeebe-env", "test")
            .withZeebeExposedPort(5701)
            .withZeebeLogger("zeebe-test")
            .build();

    // then
    final ZeebeContainer zeebeContainer = runtime.getZeebeContainer();

    assertThat(zeebeContainer.getDockerImageName())
        .isEqualTo(zeebeDockerImageName + ":" + zeebeDockerImageVersion);
    assertThat(zeebeContainer.getEnvMap()).containsEntry("zeebe-env", "test");
    assertThat(zeebeContainer.getExposedPorts()).contains(5701);
  }

  @Test
  void shouldConfigureElasticsearchDockerImage() {
    // given
    final String elasticsearchDockerImageName = "docker.elastic.co/elasticsearch/elasticsearch";
    final String elasticsearchDockerImageVersion = "8.14.0";

    // when
    final CamundaContainerRuntime runtime =
        CamundaContainerRuntime.newBuilder()
            .withElasticsearchDockerImageName(elasticsearchDockerImageName)
            .withElasticsearchDockerImageVersion(elasticsearchDockerImageVersion)
            .withElasticsearchEnv("es-env", "test")
            .withElasticsearchExposedPort(9300)
            .withElasticsearchLogger("es-test")
            .build();

    // then
    final ElasticsearchContainer elasticsearchContainer = runtime.getElasticsearchContainer();

    assertThat(elasticsearchContainer.getDockerImageName())
        .isEqualTo(elasticsearchDockerImageName + ":" + elasticsearchDockerImageVersion);
    assertThat(elasticsearchContainer.getEnvMap()).containsEntry("es-env", "test");
    assertThat(elasticsearchContainer.getExposedPorts()).contains(9300);
  }
}
