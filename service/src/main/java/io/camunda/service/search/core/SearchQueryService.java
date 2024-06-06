/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.search.core;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.ApiServices;
import io.camunda.service.search.query.SearchQueryBase;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;

public abstract class SearchQueryService<T extends ApiServices<T>, Q extends SearchQueryBase, D>
    extends ApiServices<T> {

  protected final SearchClientBasedQueryExecutor executor;

  protected SearchQueryService(
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(searchClient, transformers, authentication);
    executor = initiateExecutor();
  }

  private SearchClientBasedQueryExecutor initiateExecutor() {
    return new SearchClientBasedQueryExecutor(searchClient, transformers, authentication);
  }

  public abstract SearchQueryResult<D> search(final Q query);
}
