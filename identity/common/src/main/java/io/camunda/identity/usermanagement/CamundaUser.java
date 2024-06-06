/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement;

import java.util.Objects;

public class CamundaUser {
  private Long id;
  private String username;
  private String email;
  private boolean enabled;

  public CamundaUser() {}

  public CamundaUser(
      final Long id, final String username, final String email, final boolean enabled) {
    this.id = id;
    this.username = username;
    this.email = email;
    this.enabled = enabled;
  }

  public CamundaUser(final String username) {
    this(username, null);
  }

  public CamundaUser(final String username, final String email) {
    this(username, email, true);
  }

  public CamundaUser(final String username, final String email, final boolean enabled) {
    this(null, username, email, enabled);
  }

  public Long getId() {
    return id;
  }

  public void setId(final Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(final String email) {
    this.email = email;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, username, email, enabled);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final CamundaUser user = (CamundaUser) o;
    return enabled == user.enabled
        && Objects.equals(id, user.id)
        && Objects.equals(username, user.username)
        && Objects.equals(email, user.email);
  }
}
