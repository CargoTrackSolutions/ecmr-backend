/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services.tan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class PhoneMessageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "smso", name = "api-key")
    public PhoneMessageProvider smsoPhoneMessageProvider(@Value("${smso.api-key}") String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return null; // This will trigger @ConditionalOnMissingBean below
        }
        log.info("Configuring SMSO as PhoneMessageProvider");
        return new SmsoPhoneMessageProvider();
    }

    @Bean
    @ConditionalOnMissingBean(PhoneMessageProvider.class)
    public PhoneMessageProvider dummyMessageProvider() {
        log.info("Configuring DummyMessageProvider (logging only)");
        return new DummyMessageProvider();
    }
}
