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
import org.openlogisticsfoundation.ecmr.domain.models.EcmrSync;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrSyncEntity;

@Mapper(componentModel = "spring")
public interface EcmrSyncPersistenceMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "errorMessage", ignore = true)
    @Mapping(target = "creationTimestamp", ignore = true)
    @Mapping(target = "nextRetryTimestamp", ignore = true)
    @Mapping(target = "retryCount", ignore = true)
    EcmrSyncEntity toEcmrSyncEntity(EcmrSync ecmrSync);

    @Mapping(target = "seal", ignore = true)
    @Mapping(target = "shareToken", ignore = true)
    EcmrSync toEcmrSync(EcmrSyncEntity ecmrSyncEntity);
}
