/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.containers;

public class ContainerRuntimeDefaults {

  // Zeebe
  public static final String ZEEBE_DOCKER_IMAGE_NAME = "camunda/zeebe";
  public static final String ZEEBE_DOCKER_IMAGE_VERSION = "latest";
  public static final String ZEEBE_LOGGER_NAME = "tc.zeebe";

  // Elasticsearch
  public static final String ELASTICSEARCH_DOCKER_IMAGE_NAME = "elasticsearch";
  public static final String ELASTICSEARCH_DOCKER_IMAGE_VERSION = "8.13.0";
  public static final String ELASTICSEARCH_LOGGER_NAME = "tc.elasticsearch";
}
