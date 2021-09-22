/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.security.ldap;

import io.camunda.operate.webapp.rest.exception.UserNotFoundException;
import io.camunda.operate.webapp.security.UserService;
import java.util.List;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.rest.dto.UserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.ldap.userdetails.LdapUserDetails;
import org.springframework.stereotype.Component;

import static io.camunda.operate.webapp.security.OperateURIs.LDAP_AUTH_PROFILE;
import static io.camunda.operate.webapp.security.Permission.READ;
import static io.camunda.operate.webapp.security.Permission.WRITE;

@Component
@Profile(LDAP_AUTH_PROFILE)
public class LDAPUserService implements UserService<Authentication> {

  private static final Logger logger = LoggerFactory.getLogger(LDAPUserService.class);

  @Autowired
  private LdapTemplate ldapTemplate;

  @Autowired
  private OperateProperties operateProperties;

  @Override
  public UserDto createUserDtoFrom(
      final Authentication authentication) {
    LdapUserDetails userDetails = (LdapUserDetails) authentication.getPrincipal();
    final String dn = userDetails.getDn();
    try {
      return ldapTemplate
          .lookup(dn,
              new LdapUserAttributesMapper());
    } catch (Exception ex) {
      throw new UserNotFoundException(String.format("Couldn't find user for %s", dn));
    }
  }

  private class LdapUserAttributesMapper implements AttributesMapper<UserDto> {

    private LdapUserAttributesMapper() {
    }

    public UserDto mapFromAttributes(Attributes attrs) throws NamingException {
      final UserDto userDto = new UserDto().setCanLogout(true);
      final Attribute userIdAttr = attrs.get(operateProperties.getLdap().getUserIdAttrName());
      if (userIdAttr != null) {
        userDto.setUserId((String) userIdAttr.get());
      }
      final Attribute displayNameAttr = attrs.get(operateProperties.getLdap().getDisplayNameAttrName());
      if (displayNameAttr != null) {
        userDto.setDisplayName((String) displayNameAttr.get());
      }
      // for now can do all TODO: how to retrieve LDAP Roles/Permissions ?
      userDto.setPermissions(List.of(READ, WRITE));
      return userDto;
    }
  }
}
