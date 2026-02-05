/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.exceptions;

public class ExternalUserManagementNotAvailableException extends Exception {
    public ExternalUserManagementNotAvailableException() {
        super("External user management not implemented");
    }
}
