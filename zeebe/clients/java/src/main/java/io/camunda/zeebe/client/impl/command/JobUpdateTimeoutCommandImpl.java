/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.zeebe.client.impl.command;

import io.camunda.zeebe.client.CredentialsProvider.StatusCode;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1;
import io.camunda.zeebe.client.api.command.UpdateTimeoutJobCommandStep1.UpdateTimeoutJobCommandStep2;
import io.camunda.zeebe.client.api.response.UpdateTimeoutJobResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.UpdateTimeoutJobResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutRequest.Builder;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.UpdateJobTimeoutResponse;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class JobUpdateTimeoutCommandImpl
    implements UpdateTimeoutJobCommandStep1, UpdateTimeoutJobCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;

  public JobUpdateTimeoutCommandImpl(
      final GatewayStub asyncStub,
      final long jobKey,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = UpdateJobTimeoutRequest.newBuilder();
    builder.setJobKey(jobKey);
  }

  @Override
  public UpdateTimeoutJobCommandStep2 timeout(final long timeout) {
    builder.setTimeout(timeout);
    return this;
  }

  @Override
  public UpdateTimeoutJobCommandStep2 timeout(final Duration timeout) {
    return timeout(timeout.toMillis());
  }

  @Override
  public FinalCommandStep<UpdateTimeoutJobResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<UpdateTimeoutJobResponse> send() {
    final UpdateJobTimeoutRequest request = builder.build();

    final RetriableClientFutureImpl<
            UpdateTimeoutJobResponse, GatewayOuterClass.UpdateJobTimeoutResponse>
        future =
            new RetriableClientFutureImpl<>(
                UpdateTimeoutJobResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final UpdateJobTimeoutRequest request,
      final StreamObserver<UpdateJobTimeoutResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .updateJobTimeout(request, streamObserver);
  }

  @Override
  public UpdateTimeoutJobCommandStep2 operationReference(final long operationReference) {
    builder.setOperationReference(operationReference);
    return this;
  }
}
