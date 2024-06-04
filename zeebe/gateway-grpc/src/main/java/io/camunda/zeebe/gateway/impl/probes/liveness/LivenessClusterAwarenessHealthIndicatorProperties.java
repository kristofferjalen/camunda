/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.liveness;

import io.camunda.zeebe.util.health.AbstractDelayedHealthIndicatorProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "management.health.liveness.gateway-clusterawareness")
public class LivenessClusterAwarenessHealthIndicatorProperties
    extends AbstractDelayedHealthIndicatorProperties {

  @Override
  protected Duration getDefaultMaxDowntime() {
    return Duration.ofMinutes(5);
  }
}
