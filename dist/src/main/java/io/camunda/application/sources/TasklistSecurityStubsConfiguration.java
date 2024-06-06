/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.sources;

import io.camunda.operate.webapp.security.UserService;
import io.camunda.tasklist.property.IdentityProperties;
import io.camunda.tasklist.webapp.graphql.entity.UserDTO;
import io.camunda.tasklist.webapp.security.AssigneeMigrator;
import io.camunda.tasklist.webapp.security.AssigneeMigratorNoImpl;
import io.camunda.tasklist.webapp.security.Permission;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.security.UserReader;
import io.camunda.tasklist.webapp.security.identity.IdentityAuthorizationService;
import io.camunda.tasklist.webapp.security.tenant.TenantService;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration
public class TasklistSecurityStubsConfiguration {

  @Bean
  public UserReader stubUserReader(final UserService userService) {
    return new UserReader() {

      @Override
      public UserDTO getCurrentUser() {
        final var operateUserDto = userService.getCurrentUser();
        return new UserDTO()
            .setUserId(operateUserDto.getUserId())
            .setDisplayName(operateUserDto.getDisplayName())
            .setPermissions(List.of(Permission.READ, Permission.WRITE));
      }

      @Override
      public Optional<UserDTO> getCurrentUserBy(final Authentication authentication) {
        return Optional.empty();
      }

      @Override
      public String getCurrentOrganizationId() {
        return "";
      }

      @Override
      public String getCurrentUserId() {
        return getCurrentUser().getUserId();
      }

      @Override
      public List<UserDTO> getUsersByUsernames(final List<String> usernames) {
        return List.of();
      }

      @Override
      public Optional<String> getUserToken(final Authentication authentication) {
        return Optional.empty();
      }
    };
  }

  @Bean
  public TenantService stubTenantService() {
    return new TenantService() {
      @Override
      public AuthenticatedTenants getAuthenticatedTenants() {
        return AuthenticatedTenants.allTenants();
      }

      @Override
      public boolean isTenantValid(final String tenantId) {
        return true;
      }

      @Override
      public boolean isMultiTenancyEnabled() {
        return false;
      }
    };
  }

  @Bean
  public AssigneeMigrator stubAssigneeMigrator() {
    return new AssigneeMigratorNoImpl();
  }

  @Bean
  public IdentityAuthorizationService stubIdentityAuthorizationService() {
    return new IdentityAuthorizationService() {

      @Override
      public List<String> getUserGroups() {
        return List.of(IdentityProperties.FULL_GROUP_ACCESS);
      }

      @Override
      public boolean isAllowedToStartProcess(final String processDefinitionKey) {
        return true;
      }

      @Override
      public List<String> getProcessReadFromAuthorization() {
        return List.of(IdentityProperties.ALL_RESOURCES);
      }

      @Override
      public List<String> getProcessDefinitionsFromAuthorization() {
        return List.of(IdentityProperties.ALL_RESOURCES);
      }
    };
  }

  @Bean
  public TasklistProfileService stubTasklistProfileService() {
    return new TasklistProfileService() {

      @Override
      public String getMessageByProfileFor(final Exception exception) {
        return "";
      }

      @Override
      public boolean currentProfileCanLogout() {
        return false;
      }

      @Override
      public boolean isDevelopmentProfileActive() {
        return false;
      }

      @Override
      public boolean isSSOProfile() {
        return false;
      }

      @Override
      public boolean isIdentityProfile() {
        return false;
      }

      @Override
      public boolean isLoginDelegated() {
        return false;
      }
    };
  }
}
