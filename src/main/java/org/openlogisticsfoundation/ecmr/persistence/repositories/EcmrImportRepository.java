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

import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.projections.PendingInstanceProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EcmrImportRepository extends JpaRepository<EcmrImportEntity, Long> {
    @Query("SELECT ecmrImport.instanceUrl AS url, COUNT(ecmrImport.instanceUrl) AS count FROM EcmrImportEntity ecmrImport WHERE ecmrImport.instanceUrl NOT in (SELECT url FROM ApprovedUrlEntity ) GROUP BY ecmrImport.instanceUrl ORDER BY count DESC")
    List<PendingInstanceProjection> countAllGroupByInstanceUrl();

    List<EcmrImportEntity> findAllByImportTimestampNull();

    List<EcmrImportEntity> findAllByErrorMessageNullAndImportTimestampNull();

    @Modifying
    @Query("update EcmrImportEntity e set e.errorMessage = :errorMessage where e.id = :ecmrImportId")
    void updateEcmrImportStatus(@Param("errorMessage") String errorMessage, @Param("ecmrImportId") long ecmrImportId);

    boolean existsByEcmrId(UUID ecmrId);
}
