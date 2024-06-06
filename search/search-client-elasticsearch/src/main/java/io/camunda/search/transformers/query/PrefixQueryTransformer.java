/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.query;

import co.elastic.clients.elasticsearch._types.query_dsl.PrefixQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import io.camunda.search.clients.query.SearchPrefixQuery;
import io.camunda.search.transformers.ElasticsearchTransformers;

public final class PrefixQueryTransformer
    extends QueryOptionTransformer<SearchPrefixQuery, PrefixQuery> {

  public PrefixQueryTransformer(final ElasticsearchTransformers transformers) {
    super(transformers);
  }

  @Override
  public PrefixQuery apply(final SearchPrefixQuery value) {
    final var field = value.field();
    final var fieldValue = value.value();
    return QueryBuilders.prefix().field(field).value(fieldValue).build();
  }
}
