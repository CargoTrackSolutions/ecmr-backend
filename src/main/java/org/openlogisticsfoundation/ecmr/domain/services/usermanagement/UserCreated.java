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
 * To do something when a user is created. All implementations will be called within user creation.
 */
public interface UserCreated {
    /**
     * @param user User that was created
     */
    void onUserCreated(User user);
}
