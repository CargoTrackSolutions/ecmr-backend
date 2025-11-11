/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.mappers;

import org.mapstruct.Mapper;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.projections.PendingInstanceProjection;
import org.openlogisticsfoundation.ecmr.web.models.PendingInstanceModel;

@Mapper(componentModel = "spring")
public interface EcmrImportPersistenceMapper {
    EcmrImport toEcmrImport(EcmrImportEntity em);
    EcmrImportEntity toEcmrImportEntity(EcmrImport em);
    PendingInstanceModel toPendingInstanceModel(PendingInstanceProjection projection);
}
