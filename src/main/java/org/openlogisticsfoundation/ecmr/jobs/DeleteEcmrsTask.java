/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.jobs;

import org.openlogisticsfoundation.ecmr.domain.services.DeleteOldEcmrsService;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Component
@AllArgsConstructor
@Profile("jobs")
@Log4j2
public class DeleteEcmrsTask {

    private final DeleteOldEcmrsService deleteOldEcmrsService;

    @Scheduled(cron = "${ecmr.cron.delete}", zone = "UTC")
    public void deleteOldEcmrs(){
        log.info("--- Delete old Ecmrs Job started");
        deleteOldEcmrsService.deleteOldEcmrs();
        log.info("--- Delete old Ecmrs Job end");
    }
}
