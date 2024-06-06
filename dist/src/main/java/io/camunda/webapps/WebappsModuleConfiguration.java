/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.webapps.WebappsModuleConfiguration.WebappsProperties;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

/** Configuration for shared spring components used by webapps */
@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.webapps")
@EnableAutoConfiguration
@EnableConfigurationProperties(WebappsProperties.class)
@ConditionalOnProperty(WebappsConfigurationInitializer.CAMUNDA_WEBAPPS_ENABLED_PROPERTY)
public class WebappsModuleConfiguration {

  @Bean
  @Primary
  @Profile("!standalone && !test")
  public ObjectMapper objectMapper() {
    return Jackson2ObjectMapperBuilder.json().build();
  }

  @ConfigurationProperties("camunda.webapps")
  public record WebappsProperties(boolean enabled, String defaultApp, boolean loginDelegated) {}
}
