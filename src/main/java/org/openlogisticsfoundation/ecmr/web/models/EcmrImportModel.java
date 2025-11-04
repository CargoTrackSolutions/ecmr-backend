/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.models;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class EcmrImportModel {
    private Long id;
    private String ecmrId;
    private String instanceUrl;
    private String sharingUserEmail;
    private String receivingUserEmail;
    private String shareToken;
    private String errorMessage;
    private Instant creationTimestamp;
}
