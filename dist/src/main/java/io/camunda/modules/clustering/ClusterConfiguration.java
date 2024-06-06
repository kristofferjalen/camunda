/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.modules.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.utils.Version;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.VersionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class ClusterConfiguration {

  private final ClusterConfig config;

  @Autowired
  public ClusterConfiguration(final ClusterConfig config, final ActorScheduler scheduler) {
    this.config = config;
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster() {
    return new AtomixCluster(config, Version.from(VersionUtil.getVersion()));
  }
}
