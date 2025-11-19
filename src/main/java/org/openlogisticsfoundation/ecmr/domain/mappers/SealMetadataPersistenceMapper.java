/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.mappers;

import java.util.UUID;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openlogisticsfoundation.ecmr.api.model.SealMetadata;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;

@Mapper(componentModel = "spring")
public interface SealMetadataPersistenceMapper {
    SealMetadata toDomain(SealMetadataEntity sealMetadataEntity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "created", ignore = true)
    @Mapping(target = "last_updated", ignore = true)
    SealMetadataEntity toEntity(SealMetadata sealMetadata, UUID ecmrId);
}
