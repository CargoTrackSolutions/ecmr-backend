/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.repositories;

import org.openlogisticsfoundation.ecmr.persistence.entities.SealEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SealRepository extends JpaRepository<SealEntity, Long> {
    Optional<SealEntity> findByMetadataId(long sealMetadataId);
}
