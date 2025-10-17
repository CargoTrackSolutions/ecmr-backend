/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.exceptions;

import java.util.List;
import java.util.UUID;

public class EcmrsNotFoundException extends Exception {
    public EcmrsNotFoundException(List<UUID> ecmrIds) {
        super("Ecmrs with the following ids were not found: " + ecmrIds.toString());
    }
}
