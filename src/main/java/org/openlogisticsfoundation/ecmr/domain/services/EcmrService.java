/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrsNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrTransportType;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrType;
import org.openlogisticsfoundation.ecmr.domain.models.Group;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.SortingField;
import org.openlogisticsfoundation.ecmr.domain.models.SortingOrder;
import org.openlogisticsfoundation.ecmr.domain.models.commands.FilterRequestCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrIdProjection;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.openlogisticsfoundation.ecmr.web.models.EcmrPageModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrService {
    private final EcmrRepository ecmrRepository;
    private final EcmrPersistenceMapper ecmrPersistenceMapper;
    private final GroupService groupService;
    private final AuthorisationService authorisationService;

    @Transactional
    public EcmrModel getEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws EcmrNotFoundException, NoPermissionException {
        if (authorisationService.hasNoRole(internalOrExternalUser, ecmrId)) {
            throw new NoPermissionException("No permission to load ecmr");
        }
        EcmrEntity ecmrEntity = ecmrRepository.findByEcmrId(ecmrId).orElseThrow(() -> new EcmrNotFoundException(ecmrId));
        return ecmrPersistenceMapper.toModel(ecmrEntity);
    }

    @Transactional
    public EcmrEntity getEcmrEntity(UUID ecmrId) throws EcmrNotFoundException {
        return ecmrRepository.findByEcmrId(ecmrId).orElseThrow(() -> new EcmrNotFoundException(ecmrId));
    }

    @Transactional
    public List<EcmrEntity> getEcmrEntities(List<UUID> ecmrIds) throws EcmrsNotFoundException {
        List<EcmrEntity> entities = ecmrRepository.findAllByEcmrIdIn(ecmrIds);
        if (entities.size() != ecmrIds.size()) {
            Set<UUID> foundIds = entities.stream().map(EcmrEntity::getEcmrId).collect(Collectors.toSet());
            List<UUID> missingIds = ecmrIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toList());
            throw new EcmrsNotFoundException(missingIds);
        }
        return entities;
    }

    public boolean existsByEcmrId(UUID ecmrId) {
        return this.ecmrRepository.existsByEcmrId(ecmrId);
    }

    @Transactional
    public EcmrPageModel getEcmrsForUser(AuthenticatedUser authenticatedUser, EcmrType ecmrType, int page, int size, SortingField sortBy,
            SortingOrder sortingOrder, FilterRequestCommand filterRequestCommand) {
        Sort.Direction sortDirection = Sort.Direction.fromString(sortingOrder.name());
        final Pageable pageable = PageRequest.of(page, size, sortDirection, sortBy.getEntryFieldName());

        List<Group> usersGroups = groupService.getGroupsForUser(authenticatedUser);
        List<Long> usersGroupIds = groupService.flatMapGroupTrees(usersGroups).stream().map(Group::getId).toList();

        Boolean isInternational = filterRequestCommand.getTransportType() == null ?
                null :
                filterRequestCommand.getTransportType().equals(EcmrTransportType.International);

        final Page<EcmrIdProjection> ecmrPage = ecmrRepository.findAllByTypeAndAssignedGroupIds(ecmrType, usersGroupIds,
                filterRequestCommand.getReferenceId(), filterRequestCommand.getFrom(), filterRequestCommand.getTo(),
                isInternational, filterRequestCommand.getStatus(), filterRequestCommand.getLicensePlate(),
                filterRequestCommand.getCarrierName(), filterRequestCommand.getCarrierPostCode(), filterRequestCommand.getConsigneePostCode(),
                filterRequestCommand.getLastEditor(),
                pageable);

        List<Long> idsInOrder = ecmrPage.get().map(EcmrIdProjection::getId).toList();
        List<EcmrEntity> entities = ecmrRepository.findAllByIdIn(idsInOrder);

        //Map for reordering
        Map<Long, EcmrEntity> entityMap = entities.stream()
                .collect(Collectors.toMap(EcmrEntity::getId, Function.identity()));

        // Reordering
        List<EcmrEntity> sortedEntities = idsInOrder.stream()
                .map(entityMap::get)
                .filter(Objects::nonNull)
                .toList();

        return new EcmrPageModel(ecmrPage.getTotalPages(), ecmrPage.getTotalElements(),
                sortedEntities.stream().map(ecmrPersistenceMapper::toModel).toList());
    }

    @Transactional
    public List<EcmrRole> getCurrentEcmrRoles(@Valid @NotNull UUID ecmrId, @Valid @NotNull InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException {
        if (!this.existsByEcmrId(ecmrId)) {
            throw new EcmrNotFoundException(ecmrId);
        }
        return authorisationService.getRolesOfUser(internalOrExternalUser, ecmrId);
    }

    public EcmrEntity cleanPhoneNumbers(EcmrEntity ecmrEntity) {
        ecmrEntity.getCarrierInformation().setPhone(
                cleanPhoneNumber(ecmrEntity.getCarrierInformation().getPhone()));

        ecmrEntity.getSenderInformation().setPhone(
                cleanPhoneNumber(ecmrEntity.getSenderInformation().getPhone()));

        ecmrEntity.getConsigneeInformation().setPhone(
                cleanPhoneNumber(ecmrEntity.getConsigneeInformation().getPhone()));

        ecmrEntity.getSuccessiveCarrierInformation().setPhone(
                cleanPhoneNumber(ecmrEntity.getSuccessiveCarrierInformation().getPhone()));

        return ecmrEntity;
    }

    private String cleanPhoneNumber(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        String digits = phoneNumber.trim().replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }

        boolean startsWithPlus = phoneNumber.trim().startsWith("+");
        return startsWithPlus ? "+" + digits : digits;
    }
}
