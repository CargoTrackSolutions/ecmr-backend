/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.models;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
@Getter
@Setter
public class EcmrImport {
    private Long id;
    private UUID ecmrId;
    private String instanceUrl;
    private String sharingUserEmail;
    private String receivingUserEmail;
    private String shareToken;
    private String seal;
    private String errorMessage;
    private Instant creationTimestamp;
}
