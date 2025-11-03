/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.repositories;

import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EcmrImportRepository extends JpaRepository<EcmrImportEntity, Long> {
}
