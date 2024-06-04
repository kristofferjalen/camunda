/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.test.appender.ListAppender;
import org.apache.logging.log4j.core.test.junit.LoggerContextRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * This test ensures that StackdriverLayout defined in zeebe-util library is working as expected. If
 * the test is failing, then probably smth was changed on Zeebe side and we need to make adjustmets
 * accordingly, including /distro/config/log4j2.xml file and docs on logging.
 */
public class StackdriverJSONLayoutTest {

  public static final String STACKDRIVER_APPENDER_NAME = "Stackdriver";

  @Rule public LoggerContextRule loggerRule = new LoggerContextRule("log4j2.xml");

  private final ObjectReader jsonReader = new ObjectMapper().reader();

  @Test
  public void testLayout() throws Exception {
    // having Stackdriver appender activated
    final LoggerContext ctx = loggerRule.getLoggerContext();
    final ListAppender app = loggerRule.getListAppender(STACKDRIVER_APPENDER_NAME);
    ctx.getRootLogger().addAppender(app);
    final Logger logger = loggerRule.getLogger();

    // when
    logger.warn("Test message");

    // then
    final List<String> messages = app.getMessages();
    assertThat(messages).hasSize(1);
    final Map<String, Object> logMap =
        jsonReader.withValueToUpdate(new HashMap<String, String>()).readValue(messages.get(0));
    assertThat(logMap.get("serviceContext")).isNotNull();
    assertThat(((Map<String, String>) logMap.get("serviceContext")).get("service"))
        .isEqualTo("customService");
    assertThat(((Map<String, String>) logMap.get("serviceContext")).get("version"))
        .isEqualTo("customVersion");
  }
}
