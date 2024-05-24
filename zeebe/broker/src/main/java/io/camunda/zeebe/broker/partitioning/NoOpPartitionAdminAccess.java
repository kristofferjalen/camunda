/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.FlowControlCfg;
import io.camunda.zeebe.logstreams.impl.flowcontrol.LimitType;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

public final class NoOpPartitionAdminAccess implements PartitionAdminAccess {

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  @Override
  public Optional<PartitionAdminAccess> forPartition(final int partitionId) {
    return Optional.empty();
  }

  @Override
  public ActorFuture<Void> takeSnapshot() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> pauseExporting() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> softPauseExporting() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> resumeExporting() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> pauseProcessing() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> resumeProcessing() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> banInstance(final long processInstanceKey) {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> configureFlowControl(final FlowControlCfg flowControlCfg) {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Map<LimitType, Limit>> getFlowControlConfiguration() {
    logCall();
    return CompletableActorFuture.completed(null);
  }

  private void logCall() {
    LOG.warn("Received call on NoOp implementation of PartitionAdminAccess");
  }
}
