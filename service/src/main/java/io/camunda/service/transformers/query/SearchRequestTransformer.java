/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.query;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.FilterBase;
import io.camunda.service.search.query.TypedSearchQuery;
import io.camunda.service.search.sort.SortOption;
import io.camunda.service.transformers.ServiceTransformer;

public interface SearchRequestTransformer<F extends FilterBase, S extends SortOption>
    extends ServiceTransformer<TypedSearchQuery<F, S>, SearchQueryRequest> {

  default SearchQueryRequest apply(final TypedSearchQuery<F, S> query) {
    return applyWithAuthentication(query, null);
  }

  default SearchQueryRequest applyWithAuthentication(
      final TypedSearchQuery<F, S> value, final SearchQuery authCheck) {
    return toSearchQueryRequest(value, authCheck);
  }

  SearchQueryRequest toSearchQueryRequest(
      final TypedSearchQuery<F, S> query, final SearchQuery authCheck);
}
