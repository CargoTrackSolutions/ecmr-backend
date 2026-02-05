/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services.usermanagement;

import org.openlogisticsfoundation.ecmr.domain.models.User;

public interface ExternalUserManagement {
    void changeMfa(User user, boolean mfaEnabled);
    void resetPassword(User user);
    boolean getExternalUserMfaStatus(User user);
}
