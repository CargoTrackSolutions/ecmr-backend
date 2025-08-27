/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.UUID;
import java.util.function.Function;

import org.openlogisticsfoundation.ecmr.domain.exceptions.ShareExternallyException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrExportResult;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportModelWithUserMail;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Service
@Log4j2
public class ExternalEcmrInstanceService {

    public EcmrExportResult importEcmr(String remoteUrl, UUID ecmrId, String shareToken) throws ShareExternallyException {
        WebClient webClient = WebClient.builder().baseUrl(remoteUrl).build();
        try {
            return webClient.get()
                    .uri("api/external/ecmr/{ecmrId}/export?shareToken={shareToken}", ecmrId, shareToken)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, logErrorResponse("Importing ECMR"))
                    .bodyToMono(EcmrExportResult.class)
                    .block();
        } catch (RuntimeException e) {
            log.error("Exception while importing ECMR: {}", e.getMessage());
            throw new ShareExternallyException(e.getMessage());
        }
    }

    public boolean exportEcmrMetaData(String remoteUrl, String originUrl, UUID ecmrId, String shareToken, String userMail) {
        WebClient webClient = WebClient.builder().baseUrl(remoteUrl).build();
        try {
            webClient.post()
                    .uri("api/external/ecmr/import")
                    .bodyValue(new EcmrImportModelWithUserMail(originUrl, ecmrId, shareToken, userMail))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, logErrorResponse("Exporting ECMR metadata"))
                    .toBodilessEntity()
                    .block();
            return true;
        } catch (RuntimeException e) {
            log.error("Exception while exporting ECMR metadata: {}", e.getMessage());
            return false;
        }
    }

    private Function<ClientResponse, Mono<? extends Throwable>> logErrorResponse(String actionDescription) {
        return clientResponse -> clientResponse.bodyToMono(String.class)
                .flatMap(errorBody -> {
                    HttpStatusCode status = clientResponse.statusCode();
                    return Mono.error(new RuntimeException(actionDescription + " failed! Status: " + status + " - " + errorBody));
                });
    }
}
