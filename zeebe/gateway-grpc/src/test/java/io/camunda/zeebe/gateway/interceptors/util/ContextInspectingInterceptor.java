/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.interceptors.util;

import io.camunda.zeebe.gateway.interceptors.InterceptorUtil;
import io.camunda.zeebe.gateway.query.QueryApi;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public final class ContextInspectingInterceptor implements ServerInterceptor {
  public static final AtomicReference<QueryApi> CONTEXT_QUERY_API = new AtomicReference<>();
  public static final AtomicReference<List<String>> CONTEXT_TENANT_IDS = new AtomicReference<>();

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      final ServerCall<ReqT, RespT> call,
      final Metadata headers,
      final ServerCallHandler<ReqT, RespT> next) {
    CONTEXT_QUERY_API.set(InterceptorUtil.getQueryApiKey().get());
    CONTEXT_TENANT_IDS.set(InterceptorUtil.getAuthorizedTenantsKey().get());
    return next.startCall(call, headers);
  }
}
