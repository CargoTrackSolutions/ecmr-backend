/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.scheduledtasks;

import org.openlogisticsfoundation.ecmr.domain.services.EcmrImportService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class ImportEcmrsTask {

    private final EcmrImportService ecmrImportService;

    @Scheduled(cron = "${ecmr.cron.import}", zone = "UTC")
    public void importEcmrs(){
        ecmrImportService.importEcmrs();
    }
}
