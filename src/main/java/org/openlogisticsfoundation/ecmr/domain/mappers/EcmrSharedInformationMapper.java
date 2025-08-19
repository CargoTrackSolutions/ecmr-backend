/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openlogisticsfoundation.ecmr.domain.models.SharedInformationModel;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;

@Mapper(componentModel = "spring")
public interface EcmrSharedInformationMapper {
    @Mapping(target = "companyName", source = "senderInformation.companyName")
    @Mapping(target = "driverName", ignore = true)
    SharedInformationModel mapSenderData(EcmrEntity ecmr);

    @Mapping(target = "companyName", source = "carrierInformation.companyName")
    @Mapping(target = "driverName", source = "carrierInformation.personName")
    SharedInformationModel mapCarrierData(EcmrEntity ecmr);

    @Mapping(target = "companyName", source = "consigneeInformation.companyName")
    @Mapping(target = "driverName", ignore = true)
    SharedInformationModel mapConsigneeData(EcmrEntity ecmr);
}
