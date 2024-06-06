/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.transformers.ElasticsearchTransformers;
import io.camunda.search.transformers.SearchTransfomer;
import io.camunda.search.transformers.search.SearchResponseTransformer;
import io.camunda.zeebe.util.Either;
import java.io.IOException;

public final class ElasticsearchSearchClient implements CamundaSearchClient {

  private final ElasticsearchClient client;
  private final ElasticsearchTransformers transformers;

  public ElasticsearchSearchClient(final ElasticsearchClient client) {
    this(client, new ElasticsearchTransformers());
  }

  public ElasticsearchSearchClient(
      final ElasticsearchClient client, final ElasticsearchTransformers transformers) {
    this.client = client;
    this.transformers = transformers;
  }

  @Override
  public <T> Either<Exception, SearchQueryResponse<T>> search(
      final SearchQueryRequest searchRequest, final Class<T> documentClass) {
    try {
      final var requestTransformer = getSearchRequestTransformer();
      final var request = requestTransformer.apply(searchRequest);
      final SearchResponse<T> rawSearchResponse = client.search(request, documentClass);
      final SearchResponseTransformer<T> searchResponseTransformer = getSearchResponseTransformer();
      final SearchQueryResponse<T> response = searchResponseTransformer.apply(rawSearchResponse);
      return Either.right(response);
    } catch (final IOException ioe) {
      return Either.left(ioe);
    } catch (final ElasticsearchException e) {
      return Either.left(e);
    }
  }

  private SearchTransfomer<SearchQueryRequest, SearchRequest> getSearchRequestTransformer() {
    return transformers.getTransformer(SearchQueryRequest.class);
  }

  private <T> SearchResponseTransformer<T> getSearchResponseTransformer() {
    return new SearchResponseTransformer<>(transformers);
  }
}
