/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.Optional;

import org.openlogisticsfoundation.ecmr.domain.exceptions.ExternalUserManagementNotAvailableException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotExternalException;
import org.openlogisticsfoundation.ecmr.domain.models.User;
import org.openlogisticsfoundation.ecmr.domain.services.usermanagement.ExternalUserManagement;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExternalUserManagementFacade {

    private final Optional<ExternalUserManagement> externalUserManagement;

    public void resetPassword(User user) throws ExternalUserManagementNotAvailableException, UserNotExternalException {
        if (externalUserManagement.isEmpty()) {
            throw new ExternalUserManagementNotAvailableException();
        }

        if(!user.isExternalAccount()) {
            throw new UserNotExternalException("Cannot reset password for internal users");
        }

        externalUserManagement.get().resetPassword(user);
    }

    public boolean getExternalUserMfaStatus(User user) throws ExternalUserManagementNotAvailableException, UserNotExternalException {
        if (externalUserManagement.isEmpty()) {
            throw new ExternalUserManagementNotAvailableException();
        }

        if(!user.isExternalAccount()) {
            throw new UserNotExternalException("Cannot get MFA status for internal users");
        }

        return externalUserManagement.get().getExternalUserMfaStatus(user);
    }

    public void changeMfa(User user, boolean mfaEnabled) throws ExternalUserManagementNotAvailableException, UserNotExternalException {
        if (externalUserManagement.isEmpty()) {
            throw new ExternalUserManagementNotAvailableException();
        }

        if(!user.isExternalAccount()) {
            throw new UserNotExternalException("Cannot change MFA for internal users");
        }

        externalUserManagement.get().changeMfa(user, mfaEnabled);
    }
}
