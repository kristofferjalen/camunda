/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.containers;

public class ContainerRuntimePorts {

  // Zeebe
  public static final int ZEEBE_COMMAND_API = 26501;
  public static final int ZEEBE_GATEWAY_API = 26500;
  public static final int ZEEBE_INTERNAL_API = 26502;
  public static final int ZEEBE_MONITORING_API = 9600;
  public static final int ZEEBE_REST_API = 8080;

  // Elasticsearch
  public static final int ELASTICSEARCH_REST_API = 9200;
}
