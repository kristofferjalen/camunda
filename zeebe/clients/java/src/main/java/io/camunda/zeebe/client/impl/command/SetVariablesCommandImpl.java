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
import io.camunda.zeebe.client.api.JsonMapper;
import io.camunda.zeebe.client.api.ZeebeFuture;
import io.camunda.zeebe.client.api.command.FinalCommandStep;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1;
import io.camunda.zeebe.client.api.command.SetVariablesCommandStep1.SetVariablesCommandStep2;
import io.camunda.zeebe.client.api.response.SetVariablesResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.SetVariablesResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.SetVariablesRequest.Builder;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class SetVariablesCommandImpl
    implements SetVariablesCommandStep1, SetVariablesCommandStep2 {

  private final GatewayStub asyncStub;
  private final Builder builder;
  private final JsonMapper jsonMapper;
  private final Predicate<StatusCode> retryPredicate;
  private Duration requestTimeout;

  public SetVariablesCommandImpl(
      final GatewayStub asyncStub,
      final JsonMapper jsonMapper,
      final long elementInstanceKey,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    this.asyncStub = asyncStub;
    this.jsonMapper = jsonMapper;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
    builder = SetVariablesRequest.newBuilder();
    builder.setElementInstanceKey(elementInstanceKey);
  }

  @Override
  public FinalCommandStep<SetVariablesResponse> requestTimeout(final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<SetVariablesResponse> send() {
    final SetVariablesRequest request = builder.build();

    final RetriableClientFutureImpl<SetVariablesResponse, GatewayOuterClass.SetVariablesResponse>
        future =
            new RetriableClientFutureImpl<>(
                SetVariablesResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);
    return future;
  }

  private void send(
      final SetVariablesRequest request,
      final StreamObserver<GatewayOuterClass.SetVariablesResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .setVariables(request, streamObserver);
  }

  @Override
  public SetVariablesCommandStep2 local(final boolean local) {
    builder.setLocal(local);
    return this;
  }

  @Override
  public SetVariablesCommandStep2 variables(final InputStream variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(final String variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.validateJson("variables", variables));
  }

  @Override
  public SetVariablesCommandStep2 variables(final Map<String, Object> variables) {
    return variables((Object) variables);
  }

  @Override
  public SetVariablesCommandStep2 variables(final Object variables) {
    ArgumentUtil.ensureNotNull("variables", variables);
    return setVariables(jsonMapper.toJson(variables));
  }

  private SetVariablesCommandStep2 setVariables(final String jsonDocument) {
    builder.setVariables(jsonDocument);
    return this;
  }

  @Override
  public SetVariablesCommandStep2 operationReference(final long operationReference) {
    builder.setOperationReference(operationReference);
    return this;
  }
}
