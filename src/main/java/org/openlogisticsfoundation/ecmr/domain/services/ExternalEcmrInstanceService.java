/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.models.EcmrExportResult;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportModelWithUserMail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Service
@Log4j2
public class ExternalEcmrInstanceService {

    public EcmrExportResult importEcmr(String remoteUrl, UUID ecmrId, String shareToken) {
        WebClient webClient = WebClient.builder().baseUrl(remoteUrl).build();
        return webClient.get()
                .uri("api/external/ecmr/{ecmrId}/export?shareToken={shareToken}", ecmrId, shareToken)
                .retrieve()
                .bodyToMono(EcmrExportResult.class).block();
    }

    public boolean exportEcmrMetaData(String remoteUrl, String originUrl, UUID ecmrId, String shareToken, String userMail) {
        WebClient webClient = WebClient.builder().baseUrl(remoteUrl).build();
        try {
            ResponseEntity<Void> response = webClient.post()
                    .uri("api/external/ecmr/import")
                    .bodyValue(new EcmrImportModelWithUserMail(originUrl, ecmrId, shareToken, userMail))
                    .exchangeToMono(clientResponse -> {
                        if (clientResponse.statusCode().is2xxSuccessful()) {
                            return clientResponse.toBodilessEntity();
                        } else {
                            log.info("Error while exporting ecmr metadata: {}", clientResponse.statusCode());
                            return Mono.just(new ResponseEntity<>(clientResponse.statusCode()));
                        }
                    })
                    .block();
            return response != null && response.getStatusCode() == HttpStatus.OK;
        } catch (WebClientRequestException e) {
            log.info("Exception while exporting ecmr metadata: {}", e.getMessage());
            log.debug(e);
            return false;
        }
    }
}
