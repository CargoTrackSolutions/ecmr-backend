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

import org.apache.commons.lang3.RandomStringUtils;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.exceptions.GroupNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ActionType;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrType;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.User;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrAssignmentEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.GroupEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrAssignmentRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrCreationService {
    private final EcmrPersistenceMapper persistenceMapper;
    private final EcmrRepository ecmrRepository;
    private final EcmrAssignmentRepository ecmrAssignmentRepository;
    private final GroupService groupService;
    private final AuthorisationService authorisationService;
    private final EcmrService ecmrService;
    private final HistoryLogService historyLogService;
    private final EcmrStatusService ecmrStatusService;

    public EcmrModel createEcmr(EcmrCommand ecmrCommand, AuthenticatedUser authenticatedUser, List<Long> groupIds)
            throws NoPermissionException {
        if (groupService.isOneGroupIdNotPartOfUsersGroups(authenticatedUser, groupIds)) {
            throw new NoPermissionException("No permission for at least one group id");
        }
        if (!authorisationService.validateSaveCommand(ecmrCommand)) {
            throw new NoPermissionException("Save command is not valid");
        }
        List<GroupEntity> groupEntities = groupService.getGroupEntities(groupIds);
        EcmrEntity ecmrEntity = this.persistenceMapper.toEntity(ecmrCommand, EcmrType.ECMR, EcmrStatus.NEW);
        String fullname = this.createFullname(authenticatedUser.getUser());
        ecmrEntity.setCreatedBy(fullname);
        ecmrEntity.setCreatedAt(Instant.now());
        ecmrEntity = this.createEcmr(ecmrEntity, EcmrType.ECMR, fullname, null, ActionType.Creation);
        for (GroupEntity groupEntity : groupEntities) {
            EcmrAssignmentEntity ecmrAssignmentEntity = new EcmrAssignmentEntity();
            ecmrAssignmentEntity.setEcmr(ecmrEntity);
            ecmrAssignmentEntity.setGroup(groupEntity);
            ecmrAssignmentEntity.setRole(EcmrRole.Sender);
            ecmrAssignmentRepository.save(ecmrAssignmentEntity);
        }
        EcmrEntity entity = this.ecmrStatusService.setEcmrStatus(ecmrEntity, new InternalOrExternalUser(authenticatedUser.getUser()));
        return persistenceMapper.toModel(entity);
    }

    public EcmrEntity createTemplate(EcmrCommand ecmrCommand, AuthenticatedUser authenticatedUser) {
        EcmrEntity ecmrEntity = this.persistenceMapper.toEntity(ecmrCommand, EcmrType.TEMPLATE, EcmrStatus.NEW);
        String fullname = this.createFullname(authenticatedUser.getUser());
        ecmrEntity.setCreatedBy(fullname);
        ecmrEntity.setCreatedAt(Instant.now());
        return this.createEcmr(ecmrEntity, EcmrType.TEMPLATE, fullname, null, ActionType.Creation);
    }

    public void createEcmrFromImport(EcmrModel ecmrModel, User user, EcmrRole ecmrRole, String importToken)
            throws GroupNotFoundException, NoPermissionException {
        EcmrEntity ecmrEntity = this.persistenceMapper.toEntity(ecmrModel);
        ecmrEntity = this.createEcmr(ecmrEntity, EcmrType.ECMR, this.createFullname(user), importToken, ActionType.Import);

        GroupEntity groupEntity = groupService.getGroupEntity(user.getDefaultGroupId());

        EcmrAssignmentEntity ecmrAssignmentEntity = new EcmrAssignmentEntity();
        ecmrAssignmentEntity.setEcmr(ecmrEntity);
        ecmrAssignmentEntity.setGroup(groupEntity);
        ecmrAssignmentEntity.setRole(ecmrRole);
        ecmrAssignmentRepository.save(ecmrAssignmentEntity);
    }

    private EcmrEntity createEcmr(EcmrEntity ecmrEntity, EcmrType type, String fullName, @Nullable String importToken, ActionType actionType) {
        ecmrEntity.setShareWithSenderToken(RandomStringUtils.secure().nextAlphanumeric(4));
        ecmrEntity.setShareWithCarrierToken(RandomStringUtils.secure().nextAlphanumeric(4));
        ecmrEntity.setShareWithConsigneeToken(RandomStringUtils.secure().nextAlphanumeric(4));
        ecmrEntity.setShareWithReaderToken(RandomStringUtils.secure().nextAlphanumeric(4));
        ecmrEntity.setImportToken(importToken);
        ecmrEntity.setType(type);

        ecmrEntity = ecmrService.cleanPhoneNumbers(ecmrEntity);

        ecmrEntity = this.ecmrRepository.save(ecmrEntity);

        if (type == EcmrType.ECMR) {
            this.historyLogService.writeHistoryLog(ecmrEntity, fullName, actionType);
        }

        return ecmrEntity;
    }

    private String createFullname(User user) {
        return String.format("%s %s", user.getFirstName(), user.getLastName());
    }
}
