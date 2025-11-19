/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.mappers;

import java.time.Instant;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrImportCreateCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.projections.PendingInstanceProjection;
import org.openlogisticsfoundation.ecmr.web.models.PendingInstanceModel;

@Mapper(componentModel = "spring")
public interface EcmrImportPersistenceMapper {
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "sharingUserEmail",  ignore = true)
    @Mapping(target = "errorMessage",  ignore = true)
    EcmrImport toEcmrImport(EcmrImportCreateCommand command, String receivingUserEmail, String seal, Instant creationTimestamp);
    EcmrImport toEcmrImport(EcmrImportEntity em);
    EcmrImportEntity toEcmrImportEntity(EcmrImport em);
    PendingInstanceModel toPendingInstanceModel(PendingInstanceProjection projection);
}
