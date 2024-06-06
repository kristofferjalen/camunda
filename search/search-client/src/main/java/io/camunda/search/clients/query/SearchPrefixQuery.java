/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.query;

import io.camunda.util.ObjectBuilder;
import java.util.Objects;
import java.util.function.Function;

public final record SearchPrefixQuery(String field, String value) implements SearchQueryOption {

  static SearchPrefixQuery of(final Function<Builder, ObjectBuilder<SearchPrefixQuery>> fn) {
    return SearchQueryBuilders.prefix(fn);
  }

  public static final class Builder implements ObjectBuilder<SearchPrefixQuery> {

    private String field;
    private String value;

    public Builder field(final String value) {
      field = value;
      return this;
    }

    public Builder value(final String value) {
      this.value = value;
      return this;
    }

    @Override
    public SearchPrefixQuery build() {
      return new SearchPrefixQuery(Objects.requireNonNull(field), Objects.requireNonNull(value));
    }
  }
}
