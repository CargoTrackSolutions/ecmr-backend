/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.exceptions;

import org.springframework.http.HttpStatusCode;

public class FailedToForwardSyncException extends Exception {
    public FailedToForwardSyncException(HttpStatusCode httpStatusCode, String messageInfo, String errorMessage) {
        super(httpStatusCode + " | " + messageInfo + ": " +  errorMessage);
    }
}
