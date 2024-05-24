/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.data.os;

import static java.util.Arrays.asList;

import io.camunda.tasklist.data.DataGenerator;
import io.camunda.tasklist.data.DevDataGeneratorAbstract;
import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.entities.UserEntity;
import java.io.IOException;
import java.util.List;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev-data")
@Conditional(OpenSearchCondition.class)
public class DevDataGeneratorOpenSearch extends DevDataGeneratorAbstract implements DataGenerator {

  private static final Logger LOGGER = LoggerFactory.getLogger(DevDataGeneratorOpenSearch.class);

  @Autowired
  @Qualifier("zeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @Qualifier("openSearchClient")
  @Autowired
  private OpenSearchClient osClient;

  @Override
  public void createUser(String username, String firstname, String lastname) {
    final String password = username;
    final String passwordEncoded = passwordEncoder.encode(password);
    final UserEntity user =
        UserEntity.from(username, passwordEncoded, List.of("USER"))
            .setDisplayName(firstname + " " + lastname)
            .setRoles(asList("OWNER"));
    try {
      final IndexRequest request =
          new IndexRequest.Builder()
              .index(userIndex.getFullQualifiedName())
              .id(user.getId())
              .document(user)
              .build();
      osClient.index(request);

    } catch (Exception t) {
      LOGGER.error("Could not create demo user with user id {}", user.getUserId(), t);
    }
    LOGGER.info("Created demo user {} with password {}", username, password);
  }

  @Override
  public boolean shouldCreateData() {
    try {

      final boolean exists =
          zeebeOsClient
              .indices()
              .exists(
                  e ->
                      e.index(List.of(tasklistProperties.getZeebeOpenSearch().getPrefix() + "*"))
                          .allowNoIndices(false)
                          .ignoreUnavailable(true))
              .value();

      if (exists) {
        // data already exists
        LOGGER.debug("Data already exists in Zeebe.");
        return false;
      }
    } catch (IOException io) {
      LOGGER.debug(
          "Error occurred while checking existance of data in Zeebe: {}. Demo data won't be created.",
          io.getMessage());
      return false;
    }
    return true;
  }
}
