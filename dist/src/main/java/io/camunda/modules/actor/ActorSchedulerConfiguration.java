/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.modules.actor;

import io.camunda.modules.actor.ActorIdleStrategyConfiguration.IdleStrategySupplier;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.util.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@VisibleForTesting
public final class ActorSchedulerConfiguration {

  private final SchedulerConfiguration properties;
  private final IdleStrategySupplier idleStrategySupplier;
  private final ActorClockConfiguration actorClockConfiguration;

  @Autowired
  public ActorSchedulerConfiguration(
      final SchedulerConfiguration properties,
      final IdleStrategySupplier idleStrategySupplier,
      final ActorClockConfiguration actorClockConfiguration) {
    this.properties = properties;
    this.idleStrategySupplier = idleStrategySupplier;
    this.actorClockConfiguration = actorClockConfiguration;
  }

  @Bean(destroyMethod = "close")
  public ActorScheduler scheduler() {
    final var cpuThreads = properties.cpuThreads();
    final var ioThreads = properties.ioThreads();
    final var metricsEnabled = properties.metricsEnabled();
    final var prefix = properties.prefix();
    final var nodeId = properties.nodeId();

    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setActorClock(actorClockConfiguration.getClock().orElse(null))
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(ioThreads)
            .setMetricsEnabled(metricsEnabled)
            .setSchedulerName(String.format("%s-%s", prefix, nodeId))
            .setIdleStrategySupplier(idleStrategySupplier)
            .build();
    scheduler.start();

    return scheduler;
  }

  public record SchedulerConfiguration(
      int cpuThreads, int ioThreads, boolean metricsEnabled, String prefix, String nodeId) {}
}
