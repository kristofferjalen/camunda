/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.security.se;

import static io.camunda.tasklist.webapp.security.TasklistProfileService.AUTH_PROFILE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.graphql.spring.boot.test.GraphQLResponse;
import io.camunda.tasklist.entities.UserEntity;
import io.camunda.tasklist.metric.MetricIT;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.webapp.security.AuthenticationTestable;
import io.camunda.tasklist.webapp.security.se.store.UserStore;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** This tests: authentication and security over GraphQL API /currentUser to get current user */
@ActiveProfiles({AUTH_PROFILE, "tasklist", "test"})
public class AuthenticationIT extends TasklistIntegrationTest implements AuthenticationTestable {

  private static final String GRAPHQL_URL = "/graphql";
  private static final String CURRENT_USER_QUERY =
      "{currentUser{ userId \n displayName salesPlanType roles}}";

  private static final String USERNAME = "demo";
  private static final String PASSWORD = "demo";

  @Autowired private TestRestTemplate testRestTemplate;

  @Autowired private PasswordEncoder encoder;

  @Autowired private ObjectMapper objectMapper;

  @MockBean private UserStore userStore;

  @LocalManagementPort private int managementPort;

  @BeforeEach
  public void setUp() {
    final UserEntity user =
        new UserEntity()
            .setUserId(USERNAME)
            .setPassword(encoder.encode(PASSWORD))
            .setRoles(List.of(Role.OPERATOR.name()));
    given(userStore.getByUserId(USERNAME)).willReturn(user);
  }

  @Test
  public void testLoginSuccess() {
    // given
    // when
    final ResponseEntity<Void> response = login(USERNAME, PASSWORD);

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(response, true);
    assertThatClientConfigContains("\"canLogout\":true");
  }

  @Test
  public void shouldFailWhileLogin() {
    // when
    final ResponseEntity<Void> response = login(USERNAME, String.format("%s%d", PASSWORD, 123));

    // then
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void shouldResetCookie() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // assume
    assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreSet(loginResponse, true);
    // when
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThatCookiesAreDeleted(logoutResponse);
  }

  @Test
  public void shouldReturnIndexPageForUnknownURI() {
    // given
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            "/does-not-exist",
            HttpMethod.GET,
            prepareRequestWithCookies(loginResponse.getHeaders()),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    // TODO: How can we check that this is the index page?
    // assertThat(responseEntity.getBody()).contains("<!doctype html><html lang=\"en\">");
  }

  @Test
  public void shouldReturnCurrentUser() {
    // given authenticated user
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);
    assertThatCookiesAreSet(loginResponse, true);

    // when
    final ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);

    // then
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    final GraphQLResponse response = new GraphQLResponse(responseEntity, objectMapper);
    assertThat(response.get("$.data.currentUser.userId")).isEqualTo(USERNAME);
    assertThat(response.get("$.data.currentUser.displayName")).isEqualTo(USERNAME);
  }

  @Test
  public void testEndpointsNotAccessibleAfterLogout() {
    // when user is logged in
    final ResponseEntity<Void> loginResponse = login(USERNAME, PASSWORD);

    // then endpoint are accessible
    ResponseEntity<String> responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(loginResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(responseEntity.getBody()).isNotNull();

    // when user logged out
    final ResponseEntity<String> logoutResponse = logout(loginResponse);

    // then endpoint is not accessible
    responseEntity =
        testRestTemplate.exchange(
            GRAPHQL_URL,
            HttpMethod.POST,
            prepareRequestWithCookies(logoutResponse.getHeaders(), CURRENT_USER_QUERY),
            String.class);
    assertThat(responseEntity.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.UNAUTHORIZED);
  }

  @Test
  public void testCanAccessMetricsEndpoint() {
    final ResponseEntity<String> prometheusResponse =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + MetricIT.ENDPOINT, String.class);
    assertThat(prometheusResponse.getStatusCodeValue()).isEqualTo(200);
    assertThat(prometheusResponse.getBody()).contains("# TYPE system_cpu_usage gauge");
  }

  @Test
  @DirtiesContext
  public void testCanReadAndWriteLoggersActuatorEndpoint() throws JSONException {
    ResponseEntity<String> response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.tasklist",
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"DEBUG\"");

    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    final HttpEntity<String> request =
        new HttpEntity<>(new JSONObject().put("configuredLevel", "TRACE").toString(), headers);
    response =
        testRestTemplate.postForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.tasklist",
            request,
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(204);

    response =
        testRestTemplate.getForEntity(
            "http://localhost:" + managementPort + "/actuator/loggers/io.camunda.tasklist",
            String.class);
    assertThat(response.getStatusCodeValue()).isEqualTo(200);
    assertThat(response.getBody()).contains("\"configuredLevel\":\"TRACE\"");
  }

  @Override
  public TestRestTemplate getTestRestTemplate() {
    return testRestTemplate;
  }

  private void assertThatClientConfigContains(final String text) {
    final ResponseEntity<String> clientConfigContent =
        testRestTemplate.getForEntity("/client-config.js", String.class);
    assertThat(clientConfigContent.getBody()).contains(text);
  }
}
