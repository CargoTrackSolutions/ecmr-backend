/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.models.EcmrType;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
@Profile("jobs")
public class DeleteOldEcmrsService {

    private final EcmrDeleteService ecmrDeleteService;
    private final EcmrRepository ecmrRepository;

    @Value("${ecmr.delete.older-than-days}")
    private int deleteOlderThanDays;

    public void deleteOldEcmrs() {
        try {
            List<UUID> oldEcmrs = getOldEcmrs();

            if(!oldEcmrs.isEmpty()) {
                log.info("Deleting {} old eCMRs.", oldEcmrs.size());
                ecmrDeleteService.bulkDeleteEcmrs(oldEcmrs);
                log.info("Old eCMRs deleted.");
            } else {
                log.info("No old eCMRs found. Nothing to delete.");
            }
        } catch (Exception e) {
            log.error("Error while deleting old eCMRs: {}", e.getMessage());
        }
    }

    private List<UUID> getOldEcmrs() {
        Instant olderThan = Instant.now().minus(deleteOlderThanDays, ChronoUnit.DAYS);
        return ecmrRepository.findAllEcmrIdsByTypeAndEditedAtBefore(EcmrType.ARCHIVED, olderThan);
    }
}
