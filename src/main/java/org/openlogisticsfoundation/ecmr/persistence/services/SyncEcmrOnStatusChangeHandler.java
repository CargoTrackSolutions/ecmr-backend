/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.services;

import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.services.statuschange.EcmrStatusChanged;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrSyncEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrImportRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrSyncRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
@RequiredArgsConstructor
public class SyncEcmrOnStatusChangeHandler implements EcmrStatusChanged {

    private final EcmrSyncRepository ecmrSyncRepository;
    private final EcmrImportRepository ecmrImportRepository;

    @Override
    public void onEcmrStatusChange(EcmrStatus previousStatus, EcmrEntity ecmrEntity, InternalOrExternalUser user) {
        if(ecmrImportRepository.existsByEcmrId(ecmrEntity.getEcmrId())) {
            EcmrSyncEntity newEcmrSync = new EcmrSyncEntity();
            newEcmrSync.setEcmrId(ecmrEntity.getEcmrId());
            newEcmrSync.setRetryCount(0);
            ecmrSyncRepository.save(newEcmrSync);
        }
    }
}
