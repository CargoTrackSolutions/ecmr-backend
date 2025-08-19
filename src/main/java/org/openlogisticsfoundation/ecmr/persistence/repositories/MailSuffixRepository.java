/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.repositories;

import java.util.Optional;

import org.openlogisticsfoundation.ecmr.persistence.entities.MailSuffixEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MailSuffixRepository extends JpaRepository<MailSuffixEntity, Long> {
    Optional<MailSuffixEntity> findByMailSuffix(String mailSuffix);
}
