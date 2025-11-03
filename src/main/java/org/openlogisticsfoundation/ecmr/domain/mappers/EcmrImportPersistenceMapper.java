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

@Mapper(componentModel = "spring")
public interface EcmrImportPersistenceMapper {
    EcmrImport toEcmrImport(EcmrImportEntity em);
}
