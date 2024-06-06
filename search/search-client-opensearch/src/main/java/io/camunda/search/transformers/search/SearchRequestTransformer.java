/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.search;

import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.sort.SearchSortOptions;
import io.camunda.search.transformers.OpensearchTransformer;
import io.camunda.search.transformers.OpensearchTransformers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch.core.SearchRequest;

public final class SearchRequestTransformer
    extends OpensearchTransformer<SearchQueryRequest, SearchRequest> {

  public SearchRequestTransformer(final OpensearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public SearchRequest apply(final SearchQueryRequest value) {
    final var sort = value.sort();
    final var searchAfter = value.searchAfter();
    final var searchQuery = value.query();

    final var builder =
        new SearchRequest.Builder().index(value.index()).from(value.from()).size(value.size());

    if (searchQuery != null) {
      final var queryTransformer = getQueryTransformer();
      final var transformedQuery = queryTransformer.apply(searchQuery);
      builder.query(transformedQuery);
    }

    if (sort != null && !sort.isEmpty()) {
      builder.sort(of(sort));
    }

    if (searchAfter != null && searchAfter.length > 0) {
      builder.searchAfter(of(searchAfter));
    }

    return builder.build();
  }

  private List<SortOptions> of(final List<SearchSortOptions> values) {
    final var sortTransformer = getSortOptionsTransformer();
    return values.stream().map(sortTransformer::apply).collect(Collectors.toList());
  }

  private List<String> of(final Object[] values) {
    return Arrays.asList(values).stream().map(Object::toString).collect(Collectors.toList());
  }
}
