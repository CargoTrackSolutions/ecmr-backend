/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.web.models;

import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
public class ExternalEcmrSharingModel {
    private String senderSeal;
    private String carrierSeal;
    private String shareToken;
    private String receivingUserEmail;
    private String sharingUserEmail;
}
