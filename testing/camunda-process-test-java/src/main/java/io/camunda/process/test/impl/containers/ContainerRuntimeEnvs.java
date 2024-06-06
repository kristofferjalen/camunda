/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.containers;

public class ContainerRuntimeEnvs {

  // Zeebe
  public static final String ZEEBE_ENV_ELASTICSEARCH_CLASSNAME =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_CLASSNAME";
  public static final String ZEEBE_ENV_ELASTICSEARCH_ARGS_URL =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_URL";
  public static final String ZEEBE_ENV_ELASTICSEARCH_ARGS_BULK_SIZE =
      "ZEEBE_BROKER_EXPORTERS_ELASTICSEARCH_ARGS_BULK_SIZE";
  public static final String ZEEBE_ENV_CLOCK_CONTROLLED = "ZEEBE_CLOCK_CONTROLLED";

  // Elasticsearch
  public static String ELASTICSEARCH_ENV_XPACK_SECURITY_ENABLED = "xpack.security.enabled";
}
