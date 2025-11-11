/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.models;

import lombok.Getter;

@Getter
public class ApprovedUrlModel {
    private Long id;
    private String url;
    private boolean approvedState;
}
