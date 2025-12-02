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


@Component
@AllArgsConstructor
@Profile("jobs")
public class ArchiveEcmrsTask {

    private final EcmrUpdateService ecmrUpdateService;

    @Scheduled(cron = "${ecmr.cron.archive}", zone = "UTC")
    public void archiveEcmrs(){
        ecmrUpdateService.archiveEcmrs();
    }
}
