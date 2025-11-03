/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.repositories;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.openlogisticsfoundation.ecmr.persistence.entities.ApprovedUrlEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovedUrlRepository extends JpaRepository<ApprovedUrlEntity, Long> {
    boolean existsById(@NotNull Long id);
    List<ApprovedUrlEntity> findAllByUrlIn(@NotNull List<String> urls);
}
