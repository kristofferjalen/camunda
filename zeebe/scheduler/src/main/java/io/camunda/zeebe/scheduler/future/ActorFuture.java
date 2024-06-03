/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.future;

import io.camunda.zeebe.scheduler.ActorTask;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** interface for actor futures */
public interface ActorFuture<V> extends Future<V>, BiConsumer<V, Throwable> {
  void complete(V value);

  void completeExceptionally(String failure, Throwable throwable);

  void completeExceptionally(Throwable throwable);

  V join();

  V join(long timeout, TimeUnit timeUnit);

  /** To be used by scheduler only */
  void block(ActorTask onCompletion);

  /**
   * Registers an consumer, which is executed after the future was completed. If the caller of this
   * method is an actor, the consumer is executed in the caller's actor thread. If the caller is not
   * an actor, the consumer is executed in the actor which completes this future. If the caller is
   * not an actor, it is recommended to use {@link ActorFuture#onComplete(BiConsumer, Executor)}
   * instead.
   *
   * <p>Example:
   *
   * <p>Actor A calls Actor B to retrieve an value. Actor B returns an future, which will be
   * completed later with the right value. Actor A wants to do some work, after B returns the value.
   * For that Actor A calls `#onComplete`, at this returned future, to register an consumer. After
   * the future is completed, the registered consumer is called in the Actor A context.
   *
   * <p>Running in Actor A context:
   *
   * <pre>
   *  final ActorFuture<Value> future = ActorB.getValue();
   *  future.onComplete(value, throwable -> { // do things - runs in Actor A context again
   *  });
   * </pre>
   *
   * @param consumer the consumer which should be called after the future was completed
   */
  void onComplete(BiConsumer<V, Throwable> consumer);

  /**
   * Registers a consumer, which is executed after the future was completed. The consumer is
   * executed in the provided executor. It is recommended to not use this method if the caller is an
   * actor (use {@link ActorFuture#onComplete(BiConsumer)} instead), as it has some extra overhead
   * for synchronization.
   *
   * @param consumer the callback which should be called after the future was completed
   * @param executor the executor on which the callback will be executed
   */
  void onComplete(BiConsumer<V, Throwable> consumer, Executor executor);

  boolean isCompletedExceptionally();

  Throwable getException();

  @Override
  default void accept(final V value, final Throwable throwable) {
    if (throwable != null) {
      completeExceptionally(throwable);
    } else {
      complete(value);
    }
  }

  /**
   * Utility method to convert this future to a {@link CompletableFuture}. The returned future will
   * be completed when this future is completed.
   *
   * @return a completable future
   */
  default CompletableFuture<V> toCompletableFuture() {
    final var future = new CompletableFuture<V>();
    onComplete(
        (status, error) -> {
          if (error == null) {
            future.complete(status);
          } else {
            future.completeExceptionally(error);
          }
        },
        // Since the caller is most likely not an actor, we have to pass an executor. We use
        // Runnable, so it executes in the same actor that completes this future. This is ok because
        // the consumer passed here is not doing much to block the actor.
        Runnable::run);
    return future;
  }

  /**
   * Convenience wrapper over {@link #andThen(Function, Executor)} for the case where the next step
   * does not require the result of this future.
   */
  <U> ActorFuture<U> andThen(Supplier<ActorFuture<U>> next, Executor executor);

  /**
   * Similar to {@link CompletableFuture#thenCompose(Function)} in that it applies a function to the
   * result of this future, supporting chaining of futures while propagating exceptions.
   * Implementations may be somewhat inefficient and create intermediate futures, schedule
   * completion callbacks on the provided executor etc. As such, it should be used for orchestrating
   * futures in a non-performance critical context, for example for startup and shutdown sequences.
   *
   * @param next function to apply to the result of this future.
   * @param executor The executor used to handle completion callbacks.
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   * @param <U> the type of the new future
   */
  <U> ActorFuture<U> andThen(Function<V, ActorFuture<U>> next, Executor executor);

  /**
   * Similar to {@link #andThen(Function, Executor)}, but with better integration into the scheduler
   * module. While it creates an intermediate future for chaining, it will respect the concurrency
   * control's lifecycle. If, for example, it's closed, it will simply throw an exception on call.
   *
   * @param next function to apply to the result of this future.
   * @param executor The executor used to handle completion callbacks.
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   * @param <U> the type of the new future
   */
  default <U> ActorFuture<U> andThen(
      final Function<V, ActorFuture<U>> next, final ConcurrencyControl executor) {
    final ActorFuture<U> result = executor.createFuture();
    executor.runOnCompletion(
        this,
        (thisResult, thisError) -> {
          if (thisError != null) {
            result.completeExceptionally(thisError);
            return;
          }

          try {
            executor.runOnCompletion(next.apply(thisResult), result);
          } catch (final Exception e) {
            result.completeExceptionally(new CompletionException(e));
          }
        });

    return result;
  }

  /**
   * Similar to {@link CompletableFuture#thenApply(Function)} in that it applies a function to the
   * result of this future, allowing you to change types on the fly.
   *
   * <p>Implementations may be somewhat inefficient and create intermediate futures, schedule
   * completion callbacks on the provided executor etc. As such, it should normally be used for
   * orchestrating futures in a non-performance critical context, for example for startup and
   * shutdown sequence.
   *
   * @param next function to apply to the result of this future.
   * @param executor The executor used to handle completion callbacks.
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   * @param <U> the type of the new future
   */
  <U> ActorFuture<U> thenApply(Function<V, U> next, Executor executor);

  /**
   * Similar to {@link #thenApply(Function, Executor)}, but with better integration into the
   * scheduler module. While it creates an intermediate future for chaining, it will respect the
   * concurrency control's lifecycle. If, for example, it's closed, it will simply throw an
   * exception on call.
   *
   * @param next function to apply to the result of this future.
   * @param executor The executor used to handle completion callbacks.
   * @return a new future that completes with the result of applying the function to the result of
   *     this future or exceptionally if this future completes exceptionally. This future can be
   *     used for further chaining.
   * @param <U> the type of the new future
   */
  default <U> ActorFuture<U> thenApply(
      final Function<V, U> next, final ConcurrencyControl executor) {
    final ActorFuture<U> nextFuture = executor.createFuture();
    executor.runOnCompletion(
        this,
        (result, error) -> {
          if (error != null) {
            nextFuture.completeExceptionally(error);
            return;
          }

          try {
            nextFuture.complete(next.apply(result));
          } catch (final Exception e) {
            nextFuture.completeExceptionally(new CompletionException(e));
          }
        });
    return nextFuture;
  }
}
