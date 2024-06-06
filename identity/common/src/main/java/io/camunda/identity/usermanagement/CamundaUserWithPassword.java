/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.usermanagement;

public class CamundaUserWithPassword extends CamundaUser {
  private String password;

  public CamundaUserWithPassword() {}

  public CamundaUserWithPassword(final String username, final String password) {
    super(username);
    this.password = password;
  }

  public CamundaUserWithPassword(
      final long id,
      final String username,
      final String email,
      final boolean enabled,
      final String password) {
    super(id, username, email, enabled);
    this.password = password;
  }

  public CamundaUserWithPassword(
      final String username, final String email, final boolean enabled, final String password) {
    super(username, email, enabled);
    this.password = password;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(final String password) {
    this.password = password;
  }
}
