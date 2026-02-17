/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.List;
import java.util.Set;

import org.openlogisticsfoundation.ecmr.config.SimpleGrantedAuthority;
import org.openlogisticsfoundation.ecmr.domain.models.UserRole;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RoleService {
    public Set<String> mapUserRoleToStrings(UserRole userRole) {
        Set<String> roles = Set.of();

        if(userRole == UserRole.Admin) {
            roles = Set.of(UserRole.Admin.name(), UserRole.User.name(), UserRole.NoEcmrCreationUser.name());
        } else if(userRole == UserRole.User) {
            roles = Set.of(UserRole.User.name(), UserRole.NoEcmrCreationUser.name());
        } else if(userRole == UserRole.NoEcmrCreationUser) {
            roles = Set.of(UserRole.NoEcmrCreationUser.name());
        }

        return roles;
    }

    public List<SimpleGrantedAuthority> mapRolesToGrantedAuthorities(Set<String> userRoles) {
        return userRoles.stream().map(role -> "ROLE_" + role).map(SimpleGrantedAuthority::new).toList();
    }
}
