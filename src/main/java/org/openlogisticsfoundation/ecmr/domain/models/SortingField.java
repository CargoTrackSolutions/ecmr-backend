/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SortingField {
    referenceId("referenceIdentificationNumber"),
    from("senderInformation.companyName"),
    to("consigneeInformation.companyName"),
    status("ecmrStatus"),
    licensePlate("carrierInformation.carrierLicensePlate"),
    carrierName("carrierInformation.companyName"),
    carrierPostCode("carrierInformation.postcode"),
    consigneePostCode("consigneeInformation.postcode"),
    lastEditor("editedBy"),
    lastEditDate("editedAt"),
    creationDate("createdAt"),
    ecmrId("ecmrId");
    private final String entryFieldName;
}
