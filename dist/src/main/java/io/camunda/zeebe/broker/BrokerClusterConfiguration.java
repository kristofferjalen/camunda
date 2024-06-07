/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.ClusterConfig;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class BrokerClusterConfiguration {
  @Bean
  public ClusterConfig clusterConfig(final BrokerConfiguration config) {
    final var configFactory = new ClusterConfigFactory();
    return configFactory.mapConfiguration(config.config());
  }
}
