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
import java.util.UUID;
import java.util.stream.Collectors;

import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrsNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ActionType;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrType;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@AllArgsConstructor
@Log4j2
public class EcmrUpdateService {
    private final EcmrRepository ecmrRepository;
    private final EcmrPersistenceMapper persistenceMapper;
    private final AuthorisationService authorisationService;
    private final EcmrService ecmrService;
    private final HistoryLogService historyLogService;
    private final EcmrStatusService ecmrStatusService;

    public EcmrModel archiveEcmr(UUID ecmrUuid, AuthenticatedUser authenticatedUser)
            throws EcmrNotFoundException, ValidationException, NoPermissionException {
        if (authorisationService.hasNoRole(new InternalOrExternalUser(authenticatedUser.getUser()), ecmrUuid)) {
            throw new NoPermissionException("No permission for this task");
        }
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrUuid);
        if (ecmrEntity.getType() != EcmrType.ECMR) {
            throw new ValidationException("Only ecmrs can be archived");
        }
        ecmrEntity.setType(EcmrType.ARCHIVED);
        return persistenceMapper.toModel(this.ecmrRepository.save(ecmrEntity));
    }

    public List<EcmrModel> bulkArchiveEcmrs(List<UUID> ecmrIds, AuthenticatedUser authenticatedUser)
        throws ValidationException, NoPermissionException, EcmrsNotFoundException {

        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        for (UUID ecmrId : ecmrIds) {
            if (authorisationService.hasNoRole(user, ecmrId)) {
                throw new NoPermissionException("No permission for ECMR: " + ecmrId);
            }
        }

        List<EcmrEntity> entities = ecmrService.getEcmrEntities(ecmrIds);
        for(EcmrEntity entity : entities) {
            entity.setType(EcmrType.ARCHIVED);
        }
        return this.ecmrRepository.saveAll(entities)
            .stream()
            .map(persistenceMapper::toModel)
            .collect(Collectors.toList());
    }

    public void archiveEcmrs() {
        List<EcmrEntity> entities = ecmrRepository.findAllByEcmrStatusAndType(EcmrStatus.DELIVERED, EcmrType.ECMR);
        log.info("Archiving {} ECMRs", entities.size());
        for (EcmrEntity entity : entities) {
            entity.setType(EcmrType.ARCHIVED);
        }
        this.ecmrRepository.saveAll(entities);
    }

    public EcmrModel reactivateEcmr(UUID ecmrUuid, AuthenticatedUser authenticatedUser)
        throws EcmrNotFoundException, ValidationException, NoPermissionException {
        if (authorisationService.hasNoRole(new InternalOrExternalUser(authenticatedUser.getUser()), ecmrUuid)) {
            throw new NoPermissionException("No permission for this task");
        }
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrUuid);
        if (ecmrEntity.getType() != EcmrType.ARCHIVED) {
            throw new ValidationException("Only archived ecmrs can be reactivated");
        }
        ecmrEntity.setType(EcmrType.ECMR);
        return persistenceMapper.toModel(this.ecmrRepository.save(ecmrEntity));
    }

    @Transactional
    public EcmrModel updateEcmr(EcmrCommand ecmrCommand, UUID ecmrId, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, NoPermissionException {
        EcmrEntity ecmrEntity = ecmrRepository.findByEcmrId(ecmrId)
                .orElseThrow(() -> new EcmrNotFoundException(ecmrId));

        if (!authorisationService.validateUpdateCommand(ecmrCommand, ecmrEntity, internalOrExternalUser)) {
            throw new NoPermissionException("Update is not allowed");
        }

        ecmrEntity = persistenceMapper.toEntity(ecmrEntity, ecmrCommand, EcmrType.ECMR);

        ecmrEntity.setEditedAt(Instant.now());
        ecmrEntity.setEditedBy(internalOrExternalUser.getFullName());

        ecmrEntity = ecmrService.cleanPhoneNumbers(ecmrEntity);

        ecmrEntity = ecmrRepository.save(ecmrEntity);
        ecmrEntity = this.ecmrStatusService.setEcmrStatus(ecmrEntity, internalOrExternalUser);

        historyLogService.writeHistoryLog(ecmrEntity, internalOrExternalUser.getFullName(), ActionType.Edit);

        return persistenceMapper.toModel(ecmrEntity);
    }
}
