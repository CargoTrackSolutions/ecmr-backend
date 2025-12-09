/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrsNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrAssignmentRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrImportRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.HistoryLogRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealMetadataRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrDeleteService {
    private final AuthorisationService authorisationService;
    private final SealMetadataService sealMetadataService;
    private final EcmrService ecmrService;

    private final EcmrRepository ecmrRepository;
    private final EcmrAssignmentRepository ecmrAssignmentRepository;
    private final HistoryLogRepository historyLogRepository;
    private final EcmrImportRepository ecmrImportRepository;
    private final SealMetadataRepository sealMetadataRepository;
    private final SealRepository sealRepository;

    @Transactional
    public void deleteEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws EcmrNotFoundException, ValidationException,
            NoPermissionException {
        if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Sender)) {
            throw new NoPermissionException("No permission for this task");
        }

        if (sealMetadataService.sealExists(ecmrId)) {
            throw new ValidationException("Ecmr can not be deleted, is already sealed");
        }

        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);

        historyLogRepository.deleteAllByEcmr_EcmrId(ecmrId);
        ecmrAssignmentRepository.deleteByEcmr_EcmrId(ecmrId);

        if(ecmrImportRepository.existsByEcmrId(ecmrId)) {
            ecmrImportRepository.deleteByEcmrId(ecmrId);
        }

        ecmrRepository.delete(ecmrEntity);
    }

    @Transactional
    public void bulkDeleteEcmrs(List<UUID> ecmrIds, InternalOrExternalUser internalOrExternalUser)
            throws ValidationException, NoPermissionException, EcmrsNotFoundException {
        for (UUID ecmrId : ecmrIds) {
            if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Sender)) {
                throw new NoPermissionException("No permission for this task");
            }
            if (sealMetadataService.sealExists(ecmrId)) {
                throw new ValidationException("Ecmr can not be deleted, is already sealed");
            }
        }

        bulkDeleteEcmrs(ecmrIds);
    }

    @Transactional
    public void bulkDeleteEcmrs(List<UUID> ecmrIds) {
        List<Long> sealMetaDataIds = sealMetadataRepository.findAllByEcmrIdIn(ecmrIds).stream().map(SealMetadataEntity::getId).toList();

        historyLogRepository.deleteAllByEcmr_EcmrIdIn(ecmrIds);
        ecmrAssignmentRepository.deleteAllByEcmr_EcmrIdIn(ecmrIds);
        ecmrImportRepository.deleteAllByEcmrIdIn(ecmrIds);
        sealRepository.deleteAllByMetadataIdIn(sealMetaDataIds);
        sealMetadataRepository.deleteAllByEcmrIdIn(ecmrIds);
        ecmrRepository.deleteAllByEcmrIdIn(ecmrIds);
    }
}
