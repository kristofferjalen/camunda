/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.containers;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy.Mode;
import org.testcontainers.utility.DockerImageName;

public class ZeebeContainer extends GenericContainer<ZeebeContainer> {

  private static final Duration DEFAULT_STARTUP_TIMEOUT = Duration.ofMinutes(1);
  private static final String ZEEBE_READY_ENDPOINT = "/ready";

  private static final String ZEEBE_ELASTICSEARCH_EXPORTER_CLASSNAME =
      "io.camunda.zeebe.exporter.ElasticsearchExporter";

  public ZeebeContainer(final DockerImageName dockerImageName) {
    super(dockerImageName);
    applyDefaultConfiguration();
  }

  private void applyDefaultConfiguration() {
    withNetwork(Network.SHARED)
        .waitingFor(newDefaultWaitStrategy())
        .withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_CLOCK_CONTROLLED, "true")
        .addExposedPorts(
            ContainerRuntimePorts.ZEEBE_GATEWAY_API,
            ContainerRuntimePorts.ZEEBE_COMMAND_API,
            ContainerRuntimePorts.ZEEBE_INTERNAL_API,
            ContainerRuntimePorts.ZEEBE_MONITORING_API,
            ContainerRuntimePorts.ZEEBE_REST_API);
  }

  public ZeebeContainer withElasticsearchExporter(final String url) {
    withEnv(
        ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_CLASSNAME,
        ZEEBE_ELASTICSEARCH_EXPORTER_CLASSNAME);
    withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_ARGS_URL, url);
    withEnv(ContainerRuntimeEnvs.ZEEBE_ENV_ELASTICSEARCH_ARGS_BULK_SIZE, "1");
    return this;
  }

  public static HttpWaitStrategy newDefaultBrokerReadyCheck() {
    return new HttpWaitStrategy()
        .forPath(ZEEBE_READY_ENDPOINT)
        .forPort(ContainerRuntimePorts.ZEEBE_MONITORING_API)
        .forStatusCodeMatching(status -> status >= 200 && status < 300)
        .withReadTimeout(Duration.ofSeconds(10));
  }

  private WaitAllStrategy newDefaultWaitStrategy() {
    return new WaitAllStrategy(Mode.WITH_OUTER_TIMEOUT)
        .withStrategy(new HostPortWaitStrategy())
        .withStrategy(newDefaultBrokerReadyCheck())
        .withStartupTimeout(DEFAULT_STARTUP_TIMEOUT);
  }

  public int getGrpcApiPort() {
    return getMappedPort(ContainerRuntimePorts.ZEEBE_GATEWAY_API);
  }

  public int getRestApiPort() {
    return getMappedPort(ContainerRuntimePorts.ZEEBE_REST_API);
  }
}
