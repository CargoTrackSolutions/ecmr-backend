/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.scheduledtasks;

import org.openlogisticsfoundation.ecmr.domain.services.MailSuffixService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
public class LoadMailSuffixesTask {

    private final MailSuffixService mailSuffixService;

    @Scheduled(initialDelay = 2000, fixedDelay = Long.MAX_VALUE)
    public void loadMailSuffixesInitially() {
        mailSuffixService.loadAndReplaceMailSuffixes();
    }

    @Scheduled(cron = "0 0 3 * * ?", zone = "UTC")
    public void loadMailSuffixes(){
        mailSuffixService.loadAndReplaceMailSuffixes();
    }
}
