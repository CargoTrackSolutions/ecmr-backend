/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.services.statuschange.EcmrStatusChangedService;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@AllArgsConstructor
@Log4j2
public class EcmrStatusService {

    private final EcmrRepository ecmrRepository;
    private final EcmrStatusChangedService ecmrStatusChangedService;
    private final SealMetadataService sealMetadataService;

    public EcmrEntity setEcmrStatus(EcmrEntity ecmrEntity, InternalOrExternalUser user) throws NoPermissionException {
        EcmrStatus previousState = ecmrEntity.getEcmrStatus();

        @Nullable
        TransportRole currentSealRole = sealMetadataService.getCurrentSealMetadataEntity(ecmrEntity.getEcmrId(), user)
                .map(SealMetadataEntity::getRole)
                .orElse(null);

        ecmrEntity.setEcmrStatus(EcmrStatus.NEW);
        if (currentSealRole == TransportRole.CONSIGNEE) {
            ecmrEntity.setEcmrStatus(EcmrStatus.DELIVERED);
        } else if (currentSealRole == TransportRole.CARRIER) {
            ecmrEntity.setEcmrStatus(EcmrStatus.IN_TRANSPORT);
        } else if (currentSealRole == TransportRole.SENDER) {
            ecmrEntity.setEcmrStatus(EcmrStatus.LOADING);
        }

        ecmrEntity = ecmrRepository.save(ecmrEntity);
        ecmrStatusChangedService.ecmrStatusChanged(previousState, ecmrEntity, user);
        return ecmrEntity;
    }
}
