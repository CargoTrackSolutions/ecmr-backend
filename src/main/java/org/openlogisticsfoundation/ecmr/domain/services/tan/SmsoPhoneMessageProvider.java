/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services.tan;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SmsoPhoneMessageProvider implements PhoneMessageProvider {

    @Value("${smso.api-key}")
    private String apiKey;

    @Value("${smso.sender-id}")
    private String senderId;

    @Value("${smso.url}")
    private String smsoUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendMessage(String recipientIdentifier, String message) throws MessageProviderException {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.set("X-Authorization", apiKey);

            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("to", recipientIdentifier);
            map.add("body", message);
            map.add("sender", senderId);

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

            log.info("Sending SMS to {} via SMSO", recipientIdentifier);
            var response = restTemplate.postForEntity(smsoUrl, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("SMS sent successfully. Response: {}", response.getBody());
            } else {
                log.error("Failed to send SMS. Status code: {}, Response: {}", response.getStatusCode(), response.getBody());
                throw new MessageProviderException("Failed to send SMS via SMSO: " + response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("Error while sending SMS via SMSO", e);
            throw new MessageProviderException("Error while sending SMS via SMSO", e);
        }
    }
}
