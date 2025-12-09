/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.repositories;

import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SealMetadataRepository extends JpaRepository<SealMetadataEntity, Long> {
    List<SealMetadataEntity> findByEcmrId(UUID ecmrId);
    List<SealMetadataEntity> findAllByEcmrIdIn(List<UUID> ecmrIds);

    boolean existsByEcmrId(UUID ecmrId);

    void deleteAllByEcmrIdIn(List<UUID> ecmrIds);
}
