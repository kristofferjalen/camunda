/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.controller;

import io.camunda.identity.usermanagement.CamundaUser;
import io.camunda.identity.usermanagement.CamundaUserWithPassword;
import io.camunda.identity.usermanagement.service.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
  private final UserService userService;

  public UserController(final UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  public CamundaUser createUser(@RequestBody final CamundaUserWithPassword user) {
    return userService.createUser(user);
  }

  @DeleteMapping("/{id}")
  public void deleteUser(@PathVariable("id") final long id) {
    userService.deleteUser(id);
  }

  @GetMapping("/{id}")
  public CamundaUser findUserById(@PathVariable("id") final long id) {
    return userService.findUserById(id);
  }

  @GetMapping
  public List<CamundaUser> findAllUsers() {
    return userService.findAllUsers();
  }

  @PutMapping("/{id}")
  public CamundaUser updateUser(
      @PathVariable("id") final long id, final CamundaUserWithPassword user) {
    return userService.updateUser(id, user);
  }
}
