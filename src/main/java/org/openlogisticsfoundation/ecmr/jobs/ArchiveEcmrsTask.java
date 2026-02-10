/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.jobs;

import org.openlogisticsfoundation.ecmr.domain.services.EcmrUpdateService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@AllArgsConstructor
@Profile("jobs")
@Log4j2
public class ArchiveEcmrsTask {

    private final EcmrUpdateService ecmrUpdateService;

    @Scheduled(cron = "${ecmr.cron.archive}", zone = "UTC")
    public void archiveEcmrs(){
        log.info("--- Archive Ecmrs Job started");
        ecmrUpdateService.archiveEcmrs();
        log.info("--- Archive Ecmrs Job started");
    }
}
