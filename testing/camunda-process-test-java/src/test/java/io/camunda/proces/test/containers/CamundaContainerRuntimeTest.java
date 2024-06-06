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
import org.junit.jupiter.api.Test;

public class CamundaContainerRuntimeTest {

  @Test
  void shouldCreateContainers() {
    // given/when
    final CamundaContainerRuntime runtime = new CamundaContainerRuntime();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isFalse();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isFalse();
  }

  @Test
  void shouldStartAndStopContainers() throws Exception {
    // given
    final CamundaContainerRuntime runtime = new CamundaContainerRuntime();

    // when
    runtime.start();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isTrue();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isTrue();

    // and when
    runtime.close();

    // then
    assertThat(runtime.getZeebeContainer()).isNotNull();
    assertThat(runtime.getZeebeContainer().isRunning()).isFalse();

    assertThat(runtime.getElasticsearchContainer()).isNotNull();
    assertThat(runtime.getElasticsearchContainer().isRunning()).isFalse();
  }
}
