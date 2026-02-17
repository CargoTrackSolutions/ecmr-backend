/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services.usermanagement;

import org.openlogisticsfoundation.ecmr.domain.models.User;

/**
 * Handling external users. Implementations are called from the UserController and checked by the CapabilitiesController.
 */
public interface ExternalUserManagement {
    /**
     * @param user User to change mfa for
     * @param mfaEnabled true to enable mfa, false to disable
     */
    void changeMfa(User user, boolean mfaEnabled);

    /**
     * @param user User to reset password for
     */
    void resetPassword(User user);

    /**
     * @param user User to get MFA status for
     * @return true if MFA is enabled for user, false otherwise
     */
    boolean getExternalUserMfaStatus(User user);
}
