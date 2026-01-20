/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openlogisticsfoundation.ecmr.api.model.SealedDocument;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidShareTokenException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.SealedDocumentNotValidException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrSync;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrSyncEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrImportRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrSyncRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealMetadataRepository;
import org.openlogisticsfoundation.ecmr.web.mappers.EcmrWebMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;

import ecmr.seal.verify.rest.ESeal;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcmrSyncService {
    private final EcmrService ecmrService;
    private final EcmrUpdateService ecmrUpdateService;
    private final SealService sealService;
    private final SealMetadataService sealMetadataService;

    private final SealMetadataRepository sealMetadataRepository;
    private final EcmrSyncRepository ecmrSyncRepository;
    private final EcmrImportRepository ecmrImportRepository;
    private final EcmrRepository ecmrRepository;

    private final EcmrWebMapper ecmrWebMapper;

    @Transactional
    public void syncEcmr(EcmrSync ecmrSync)
            throws EcmrNotFoundException, InvalidShareTokenException, SealedDocumentNotValidException, JsonProcessingException,
            NoPermissionException {
        if (!ecmrService.existsByEcmrId(ecmrSync.getEcmrId())) {
            throw new EcmrNotFoundException(ecmrSync.getEcmrId());
        }

        List<SealMetadataEntity> sealMetadataEntities = sealMetadataRepository.findByEcmrId(ecmrSync.getEcmrId());
        Map<TransportRole, String> existingSeals = new HashMap<>();

        sealMetadataEntities.forEach(sealMetadata -> {
            if (sealMetadata.getRole().equals(TransportRole.CONSIGNEE)) {
                throw new SealedDocumentNotValidException("Consignee seal already exists");
            }
            existingSeals.put(sealMetadata.getRole(), sealMetadataService.getSealByMetadataId(sealMetadata.getId()));
        });

        if (!sealService.verify(getListOfSeals(existingSeals, ecmrSync.getSeal()))) {
            throw new SealedDocumentNotValidException("Sealed document is not valid");
        }

        if (!ecmrRepository.shareTokenExists(ecmrSync.getEcmrId(), ecmrSync.getShareToken())) {
            throw new InvalidShareTokenException(
                    String.format("Share token %s is not valid for ecmr %s", ecmrSync.getShareToken(), ecmrSync.getEcmrId()));
        }

        SealedDocument sealedDocument = sealService.deserializePayloadClaimJson(new ESeal(ecmrSync.getSeal(), null), SealedDocument.class);
        SealMetadataEntity savedSealMetadata = sealMetadataService.save(sealedDocument.getSealMetadata(), ecmrSync.getEcmrId());
        sealService.saveSeal(ecmrSync.getSeal(), savedSealMetadata);

        EcmrCommand ecmrCommand = ecmrWebMapper.toCommand(sealedDocument.getEcmr());
        ecmrUpdateService.updateEcmr(ecmrCommand, ecmrSync.getEcmrId());

        if (ecmrImportRepository.existsByEcmrId(ecmrSync.getEcmrId())) {
            EcmrSyncEntity ecmrSyncEntity = new EcmrSyncEntity();
            ecmrSyncEntity.setEcmrId(ecmrSync.getEcmrId());
            ecmrSyncEntity.setRetryCount(0);
            ecmrSyncRepository.save(ecmrSyncEntity);
        }
    }

    private List<ESeal> getListOfSeals(Map<TransportRole, String> existingSeals, String newSeal) {
        List<ESeal> seals = new ArrayList<>();

        if (existingSeals.containsKey(TransportRole.CARRIER) && existingSeals.containsKey(TransportRole.SENDER)) {
            seals.addAll(List.of(new ESeal(newSeal, null),
                    new ESeal(existingSeals.get(TransportRole.CARRIER), null),
                    new ESeal(existingSeals.get(TransportRole.SENDER), null)));
        } else if (existingSeals.containsKey(TransportRole.SENDER)) {
            seals.addAll(List.of(new ESeal(newSeal, null), new ESeal(existingSeals.get(TransportRole.SENDER), null)));
        }

        return seals;
    }

}
