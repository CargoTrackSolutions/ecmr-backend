/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.models.commands;

import org.jetbrains.annotations.NotNull;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ApprovedUrlCommand {
    private Long id;
    @NotNull
    private String url;
    private boolean approvedState;
}
