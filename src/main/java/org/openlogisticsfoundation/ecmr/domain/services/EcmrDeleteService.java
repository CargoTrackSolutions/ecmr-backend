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
import java.util.stream.Collectors;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrsNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrAssignmentRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.HistoryLogRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealedDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrDeleteService {
    private final EcmrRepository ecmrRepository;
    private final EcmrAssignmentRepository ecmrAssignmentRepository;
    private final AuthorisationService authorisationService;
    private final HistoryLogRepository historyLogRepository;
    private final SealedDocumentRepository sealedDocumentRepository;
    private final EcmrService ecmrService;

    @Transactional
    public void deleteEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws EcmrNotFoundException, ValidationException,
            NoPermissionException {
        if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Sender)) {
            throw new NoPermissionException("No permission for this task");
        }

        if (sealedDocumentRepository.existsByEcmr_EcmrId(ecmrId)) {
            throw new ValidationException("Ecmr can not be deleted, is already sealed");
        }

        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);

        historyLogRepository.deleteAllByEcmr_EcmrId(ecmrId);
        ecmrAssignmentRepository.deleteByEcmr_EcmrId(ecmrId);
        ecmrRepository.delete(ecmrEntity);
    }

    @Transactional
    public void bulkDeleteEcmrs(List<UUID> ecmrIds, InternalOrExternalUser internalOrExternalUser) throws ValidationException, NoPermissionException, EcmrsNotFoundException {
        for (UUID ecmrId : ecmrIds) {
            if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Sender)) {
                throw new NoPermissionException("No permission for this task");
            }
            if (sealedDocumentRepository.existsByEcmr_EcmrId(ecmrId)) {
                throw new ValidationException("Ecmr can not be deleted, is already sealed");
            }
        }

        List<EcmrEntity> entities = ecmrService.getEcmrEntities(ecmrIds);
        List<UUID> ids = entities.stream().map(EcmrEntity::getEcmrId).collect(Collectors.toList());

        historyLogRepository.deleteAllByEcmr_EcmrIdIn(ids);
        ecmrAssignmentRepository.deleteAllByEcmr_EcmrIdIn(ids);
        ecmrRepository.deleteAll(entities);
    }

}
