/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.util;

import com.netflix.concurrency.limits.Limit;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.LogStorage;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.util.Objects;

public final class SyncLogStreamBuilder implements LogStreamBuilder {
  private final LogStreamBuilder delegate;
  private ActorSchedulingService actorSchedulingService;

  SyncLogStreamBuilder() {
    this(LogStream.builder());
  }

  SyncLogStreamBuilder(final LogStreamBuilder delegate) {
    this.delegate = delegate;
  }

  @Override
  public SyncLogStreamBuilder withActorSchedulingService(
      final ActorSchedulingService actorSchedulingService) {
    this.actorSchedulingService = actorSchedulingService;
    delegate.withActorSchedulingService(actorSchedulingService);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withMaxFragmentSize(final int maxFragmentSize) {
    delegate.withMaxFragmentSize(maxFragmentSize);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withLogStorage(final LogStorage logStorage) {
    delegate.withLogStorage(logStorage);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withPartitionId(final int partitionId) {
    delegate.withPartitionId(partitionId);
    return this;
  }

  @Override
  public SyncLogStreamBuilder withLogName(final String logName) {
    delegate.withLogName(logName);
    return this;
  }

  @Override
  public LogStreamBuilder withAppendLimit(final Limit appendLimit) {
    delegate.withAppendLimit(appendLimit);
    return this;
  }

  @Override
  public LogStreamBuilder withRequestLimit(final Limit requestLimit) {
    delegate.withRequestLimit(requestLimit);
    return this;
  }

  @Override
  public ActorFuture<LogStream> buildAsync() {
    return delegate.buildAsync();
  }

  public SyncLogStream build() {
    final var scheduler =
        Objects.requireNonNull(
            actorSchedulingService,
            "must provide an actor scheduling service through SyncLogStreamBuilder#withActorSchedulingService");

    final var buildFuture = new CompletableActorFuture<SyncLogStream>();
    scheduler.submitActor(
        new Actor() {
          @Override
          protected void onActorStarting() {
            actor.runOnCompletionBlockingCurrentPhase(
                buildAsync(),
                (logStream, t) -> {
                  if (t == null) {
                    buildFuture.complete(new SyncLogStream(logStream));
                  } else {
                    buildFuture.completeExceptionally(t);
                  }
                });
          }
        });
    return buildFuture.join();
  }
}
