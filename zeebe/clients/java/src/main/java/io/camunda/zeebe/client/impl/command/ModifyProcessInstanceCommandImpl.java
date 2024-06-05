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
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import io.camunda.zeebe.client.api.response.ModifyProcessInstanceResponse;
import io.camunda.zeebe.client.impl.RetriableClientFutureImpl;
import io.camunda.zeebe.client.impl.response.ModifyProcessInstanceResponseImpl;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc.GatewayStub;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.ActivateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.TerminateInstruction;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ModifyProcessInstanceRequest.VariableInstruction;
import io.grpc.stub.StreamObserver;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class ModifyProcessInstanceCommandImpl
    implements ModifyProcessInstanceCommandStep1, ModifyProcessInstanceCommandStep3 {

  private static final String EMPTY_SCOPE_ID = "";
  private static final long EMPTY_ANCESTOR_KEY = -1L;
  private final ModifyProcessInstanceRequest.Builder requestBuilder =
      ModifyProcessInstanceRequest.newBuilder();
  private final JsonMapper jsonMapper;
  private final GatewayStub asyncStub;
  private final Predicate<StatusCode> retryPredicate;
  private ActivateInstruction latestActivateInstruction;
  private Duration requestTimeout;

  public ModifyProcessInstanceCommandImpl(
      final long processInstanceKey,
      final JsonMapper jsonMapper,
      final GatewayStub asyncStub,
      final Duration requestTimeout,
      final Predicate<StatusCode> retryPredicate) {
    requestBuilder.setProcessInstanceKey(processInstanceKey);
    this.jsonMapper = jsonMapper;
    this.asyncStub = asyncStub;
    this.requestTimeout = requestTimeout;
    this.retryPredicate = retryPredicate;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 activateElement(final String elementId) {
    return activateElement(elementId, EMPTY_ANCESTOR_KEY);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 activateElement(
      final String elementId, final long ancestorElementInstanceKey) {
    return addActivateInstruction(elementId, ancestorElementInstanceKey);
  }

  @Override
  public ModifyProcessInstanceCommandStep2 terminateElement(final long elementInstanceKey) {
    requestBuilder.addTerminateInstructions(
        TerminateInstruction.newBuilder().setElementInstanceKey(elementInstanceKey).build());
    return this;
  }

  private ModifyProcessInstanceCommandStep3 addActivateInstruction(
      final String elementId, final long ancestorElementInstanceKey) {
    final ActivateInstruction activateInstruction =
        ActivateInstruction.newBuilder()
            .setElementId(elementId)
            .setAncestorElementInstanceKey(ancestorElementInstanceKey)
            .build();
    latestActivateInstruction = activateInstruction;
    requestBuilder.addActivateInstructions(activateInstruction);
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep1 and() {
    latestActivateInstruction = null;
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(final InputStream variables) {
    return withVariables(variables, EMPTY_SCOPE_ID);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(
      final InputStream variables, final String scopeId) {
    final VariableInstruction variableInstruction = createVariableInstruction(variables, scopeId);
    addVariableInstructionToLatestActivateInstruction(variableInstruction);
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(final String variables) {
    return withVariables(variables, EMPTY_SCOPE_ID);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(
      final String variables, final String scopeId) {
    final VariableInstruction variableInstruction = createVariableInstruction(variables, scopeId);
    addVariableInstructionToLatestActivateInstruction(variableInstruction);
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(final Map<String, Object> variables) {
    return withVariables(variables, EMPTY_SCOPE_ID);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(
      final Map<String, Object> variables, final String scopeId) {
    final VariableInstruction variableInstruction = createVariableInstruction(variables, scopeId);
    addVariableInstructionToLatestActivateInstruction(variableInstruction);
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(final Object variables) {
    return withVariables(variables, EMPTY_SCOPE_ID);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariables(
      final Object variables, final String scopeId) {
    final VariableInstruction variableInstruction = createVariableInstruction(variables, scopeId);
    addVariableInstructionToLatestActivateInstruction(variableInstruction);
    return this;
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariable(final String key, final Object value) {
    return withVariable(key, value, EMPTY_SCOPE_ID);
  }

  @Override
  public ModifyProcessInstanceCommandStep3 withVariable(
      final String key, final Object value, final String scopeId) {
    ArgumentUtil.ensureNotNull("key", key);
    return withVariables(Collections.singletonMap(key, value), scopeId);
  }

  private VariableInstruction createVariableInstruction(
      final InputStream variables, final String scopeId) {
    ArgumentUtil.ensureNotNull("variables", variables);
    final String variablesString = jsonMapper.validateJson("variables", variables);
    return createVariableInstruction(variablesString, scopeId);
  }

  private VariableInstruction createVariableInstruction(
      final Map<String, Object> variables, final String scopeId) {
    return createVariableInstruction((Object) variables, scopeId);
  }

  private VariableInstruction createVariableInstruction(
      final Object variables, final String scopeId) {
    ArgumentUtil.ensureNotNull("variables", variables);
    final String variablesString = jsonMapper.toJson(variables);
    return createVariableInstruction(variablesString, scopeId);
  }

  private VariableInstruction createVariableInstruction(
      final String variables, final String scopeId) {
    return VariableInstruction.newBuilder()
        .setVariables(jsonMapper.validateJson("variables", variables))
        .setScopeId(scopeId)
        .build();
  }

  private void addVariableInstructionToLatestActivateInstruction(
      final VariableInstruction variableInstruction) {
    // Grpc created immutable objects. Since we have already build the activate instruction before
    // (in case it has no variables), we will have to remove this instruction. We can then copy it
    // using toBuilder() and add the variable instructions we need. Then we need to re-add the
    // activate instruction.
    requestBuilder.removeActivateInstructions(
        requestBuilder.getActivateInstructionsList().indexOf(latestActivateInstruction));
    latestActivateInstruction =
        latestActivateInstruction.toBuilder().addVariableInstructions(variableInstruction).build();
    requestBuilder.addActivateInstructions(latestActivateInstruction);
  }

  @Override
  public FinalCommandStep<ModifyProcessInstanceResponse> requestTimeout(
      final Duration requestTimeout) {
    this.requestTimeout = requestTimeout;
    return this;
  }

  @Override
  public ZeebeFuture<ModifyProcessInstanceResponse> send() {
    final ModifyProcessInstanceRequest request = requestBuilder.build();

    final RetriableClientFutureImpl<
            ModifyProcessInstanceResponse, GatewayOuterClass.ModifyProcessInstanceResponse>
        future =
            new RetriableClientFutureImpl<>(
                ModifyProcessInstanceResponseImpl::new,
                retryPredicate,
                streamObserver -> send(request, streamObserver));

    send(request, future);

    return future;
  }

  private void send(
      final ModifyProcessInstanceRequest request,
      final StreamObserver<GatewayOuterClass.ModifyProcessInstanceResponse> streamObserver) {
    asyncStub
        .withDeadlineAfter(requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
        .modifyProcessInstance(request, streamObserver);
  }

  @Override
  public ModifyProcessInstanceCommandStep2 operationReference(final long operationReference) {
    requestBuilder.setOperationReference(operationReference);
    return this;
  }
}
