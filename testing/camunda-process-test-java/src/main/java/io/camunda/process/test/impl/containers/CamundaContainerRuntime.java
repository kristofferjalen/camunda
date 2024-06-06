/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.containers;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.utility.DockerImageName;

public class CamundaContainerRuntime implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(CamundaContainerRuntime.class);

  private static final String NETWORK_ALIAS_ZEEBE = "zeebe";
  private static final String NETWORK_ALIAS_ELASTICSEARCH = "elasticsearch";

  private static final String ELASTICSEARCH_URL =
      NETWORK_ALIAS_ELASTICSEARCH + ":" + ContainerRuntimePorts.ELASTICSEARCH_REST_API;

  private final Network network;
  private final ZeebeContainer zeebeContainer;
  private final ElasticsearchContainer elasticsearchContainer;

  public CamundaContainerRuntime() {
    this(new Builder());
  }

  public CamundaContainerRuntime(final Builder builder) {
    network = Network.newNetwork();

    zeebeContainer = createZeebeContainer(network, builder);
    elasticsearchContainer = createElasticsearchContainer(network, builder);
  }

  private ZeebeContainer createZeebeContainer(final Network network, final Builder builder) {
    final ZeebeContainer container =
        new ZeebeContainer(
                parseDockerImage(builder.zeebeDockerImageName, builder.zeebeDockerImageVersion))
            .withLogConsumer(createContainerLogger(builder.zeebeLoggerName))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_ZEEBE)
            .withElasticsearchExporter(ELASTICSEARCH_URL)
            .withEnv(builder.zeebeEnvVars);

    builder.zeebeExposedPorts.forEach(container::addExposedPort);

    return container;
  }

  private ElasticsearchContainer createElasticsearchContainer(
      final Network network, final Builder builder) {
    final ElasticsearchContainer container =
        new ElasticsearchContainer(
                parseDockerImage(
                    builder.elasticsearchDockerImageName, builder.elasticsearchDockerImageVersion))
            .withLogConsumer(createContainerLogger(builder.elasticsearchLoggerName))
            .withNetwork(network)
            .withNetworkAliases(NETWORK_ALIAS_ELASTICSEARCH)
            .withEnv(ContainerRuntimeEnvs.ELASTICSEARCH_ENV_XPACK_SECURITY_ENABLED, "false")
            .withEnv(builder.elasticsearchEnvVars);

    builder.elasticsearchExposedPorts.forEach(container::addExposedPort);

    return container;
  }

  public void start() {
    LOGGER.info("Starting Camunda container runtime");
    final Instant startTime = Instant.now();

    elasticsearchContainer.start();
    zeebeContainer.start();

    final Instant endTime = Instant.now();
    final Duration startupTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime started in {}", startupTime);
  }

  public ZeebeContainer getZeebeContainer() {
    return zeebeContainer;
  }

  public ElasticsearchContainer getElasticsearchContainer() {
    return elasticsearchContainer;
  }

  @Override
  public void close() throws Exception {
    LOGGER.info("Stopping Camunda container runtime");
    final Instant startTime = Instant.now();

    zeebeContainer.stop();
    elasticsearchContainer.stop();
    network.close();

    final Instant endTime = Instant.now();
    final Duration shutdownTime = Duration.between(startTime, endTime);
    LOGGER.info("Camunda container runtime stopped in {}", shutdownTime);
  }

  private static DockerImageName parseDockerImage(
      final String imageName, final String imageVersion) {
    return DockerImageName.parse(imageName).withTag(imageVersion);
  }

  private static Slf4jLogConsumer createContainerLogger(final String name) {
    final Logger logger = LoggerFactory.getLogger(name);
    return new Slf4jLogConsumer(logger);
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  public static class Builder {

    private String zeebeDockerImageName = ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_NAME;
    private String zeebeDockerImageVersion = ContainerRuntimeDefaults.ZEEBE_DOCKER_IMAGE_VERSION;

    private String elasticsearchDockerImageName =
        ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_NAME;
    private String elasticsearchDockerImageVersion =
        ContainerRuntimeDefaults.ELASTICSEARCH_DOCKER_IMAGE_VERSION;

    private final Map<String, String> zeebeEnvVars = new HashMap<>();
    private final Map<String, String> elasticsearchEnvVars = new HashMap<>();

    private final List<Integer> zeebeExposedPorts = new ArrayList<>();
    private final List<Integer> elasticsearchExposedPorts = new ArrayList<>();

    private String zeebeLoggerName = ContainerRuntimeDefaults.ZEEBE_LOGGER_NAME;
    private String elasticsearchLoggerName = ContainerRuntimeDefaults.ELASTICSEARCH_LOGGER_NAME;

    public Builder withZeebeDockerImageName(final String dockerImageName) {
      zeebeDockerImageName = dockerImageName;
      return this;
    }

    public Builder withZeebeDockerImageVersion(final String dockerImageVersion) {
      zeebeDockerImageVersion = dockerImageVersion;
      return this;
    }

    public Builder withElasticsearchDockerImageName(final String dockerImageName) {
      elasticsearchDockerImageName = dockerImageName;
      return this;
    }

    public Builder withElasticsearchDockerImageVersion(final String dockerImageVersion) {
      elasticsearchDockerImageVersion = dockerImageVersion;
      return this;
    }

    public Builder withZeebeEnvVar(final Map<String, String> envVars) {
      zeebeEnvVars.putAll(envVars);
      return this;
    }

    public Builder withZeebeEnv(final String name, final String value) {
      zeebeEnvVars.put(name, value);
      return this;
    }

    public Builder withElasticsearchEnvVar(final Map<String, String> envVars) {
      elasticsearchEnvVars.putAll(envVars);
      return this;
    }

    public Builder withElasticsearchEnv(final String name, final String value) {
      elasticsearchEnvVars.put(name, value);
      return this;
    }

    public Builder withZeebeExposedPort(final int port) {
      zeebeExposedPorts.add(port);
      return this;
    }

    public Builder withElasticsearchExposedPort(final int port) {
      elasticsearchExposedPorts.add(port);
      return this;
    }

    public Builder withZeebeLogger(final String loggerName) {
      zeebeLoggerName = loggerName;
      return this;
    }

    public Builder withElasticsearchLogger(final String loggerName) {
      elasticsearchLoggerName = loggerName;
      return this;
    }

    public CamundaContainerRuntime build() {
      return new CamundaContainerRuntime(this);
    }
  }
}
