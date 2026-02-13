/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.time.Instant;
import java.util.List;

import org.openlogisticsfoundation.ecmr.domain.exceptions.FailedToForwardSyncException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrSyncPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrSync;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrSyncEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrSyncRepository;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcmrForwardSyncService {
    private final EcmrSyncPersistenceMapper ecmrSyncPersistenceMapper;
    @PersistenceContext
    private EntityManager entityManager;

    private final EcmrImportService ecmrImportService;
    private final SealMetadataService sealMetadataService;

    private final EcmrSyncRepository ecmrSyncRepository;

    private final WebClient.Builder webClientBuilder;

    @Transactional
    public boolean forwardSyncToExternalInstance() {
        List<EcmrSyncEntity> ecmrSyncEntities = entityManager.createQuery("""
                        select e
                        from EcmrSyncEntity e
                        where (e.retryCount <= 3 OR e.retryCount IS null)
                        AND (e.nextRetryTimestamp IS null OR e.nextRetryTimestamp <= :currentDate)
                        order by e.creationTimestamp
                        """, EcmrSyncEntity.class)
                .setParameter("currentDate", Instant.now())
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        if (ecmrSyncEntities.isEmpty()) {
            return false;
        }

        EcmrSyncEntity ecmrSyncEntity = ecmrSyncEntities.getFirst();
        EcmrImportEntity ecmrImportEntity = ecmrImportService.getEcmrImportEntity(ecmrSyncEntity.getEcmrId());

        List<SealMetadataEntity> sealMetadataEntities = sealMetadataService.getSealMetadataEntities(ecmrSyncEntity.getEcmrId());
        SealMetadataEntity lastSealMetadata = sealMetadataService.getCurrentSealMetadataEntity(sealMetadataEntities).orElse(null);

        if(lastSealMetadata == null) {
            setErrorAndRetryState(ecmrSyncEntity, "NO_SEAL_FOUND");
            log.debug("No Seal found for ecmrId {}", ecmrSyncEntity.getEcmrId());
            return true;
        }
        String lastSeal = sealMetadataService.getSealByMetadataId(lastSealMetadata.getId());

        EcmrSync newSync = ecmrSyncPersistenceMapper.toEcmrSync(ecmrSyncEntity);
        newSync.setSeal(lastSeal);
        newSync.setShareToken(ecmrImportEntity.getShareToken());

        sendToExternalInstance(newSync, ecmrSyncEntity, ecmrImportEntity.getInstanceUrl());
        return true;
    }

    private void sendToExternalInstance(EcmrSync ecmrSync, EcmrSyncEntity ecmrSyncEntity, String instanceUrl) {
        WebClient webClient = webClientBuilder.baseUrl(instanceUrl).build();

        try {
            webClient.put()
                    .uri("/api/sync")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(ecmrSync)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, clientResponse ->
                            clientResponse.bodyToMono(String.class)
                                    .flatMap(errorBody ->
                                            Mono.error(new FailedToForwardSyncException(clientResponse.statusCode(),
                                                    "Failed to Forward Sync to External Instance",
                                                    errorBody)))
                    )
                    .bodyToMono(Void.class)
                    .block();

            log.info("Successfully forwarded {} Sync to: {}", ecmrSync.getEcmrId(), instanceUrl);

            ecmrSyncRepository.deleteByEcmrId(ecmrSync.getEcmrId());
        } catch (Exception e) {
            log.warn("Failed to forward Sync to External Instance: {}", e.getMessage());
            log.debug(e);
            setErrorAndRetryState(ecmrSyncEntity, "FAILED_TO_FORWARD_SYNC");
        }
    }

    private void setErrorAndRetryState(EcmrSyncEntity ecmrSync, @Nullable String error) {
        Instant nextRetryDate = Instant.now().plusSeconds(5 * 60);
        ecmrSync.setNextRetryTimestamp(nextRetryDate);
        ecmrSync.setRetryCount(ecmrSync.getRetryCount() + 1);
        if (error != null) {
            ecmrSync.setErrorMessage(error);
        }
        ecmrSyncRepository.save(ecmrSync);
    }
}
