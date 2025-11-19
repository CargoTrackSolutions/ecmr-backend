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

import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrAssignmentMapper;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrAssignment;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrAssignmentEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.ExternalUserEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.GroupEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrAssignmentRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrAssignmentService {

    private final EcmrAssignmentRepository ecmrAssignmentRepository;
    private final AuthorisationService authorisationService;
    private final EcmrAssignmentMapper ecmrAssignmentMapper;

    public List<EcmrAssignment> getAssignmentsOfEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws NoPermissionException {
        if (authorisationService.hasNoRole(internalOrExternalUser, ecmrId)) {
            throw new NoPermissionException("No permission to load ecmr assignments");
        }
        return this.ecmrAssignmentRepository.findByEcmr_EcmrId(ecmrId).stream().map(ecmrAssignmentMapper::map).toList();
    }

    void createAndSaveAssigment(final EcmrEntity ecmr, final EcmrRole role, final ExternalUserEntity externalUser) {
        EcmrAssignmentEntity assignmentEntity = new EcmrAssignmentEntity();
        assignmentEntity.setEcmr(ecmr);
        assignmentEntity.setRole(role);
        assignmentEntity.setExternalUser(externalUser);
        ecmrAssignmentRepository.save(assignmentEntity);
    }

    void createAndSaveAssigment(final EcmrEntity ecmr, final EcmrRole role, final GroupEntity group) {
        EcmrAssignmentEntity assignmentEntity = new EcmrAssignmentEntity();
        assignmentEntity.setEcmr(ecmr);
        assignmentEntity.setRole(role);
        assignmentEntity.setGroup(group);
        ecmrAssignmentRepository.save(assignmentEntity);
    }

    void saveAll(List<EcmrAssignmentEntity> ecmrAssignments) {
        this.ecmrAssignmentRepository.saveAll(ecmrAssignments);
    }

    int getRegistrationCountInLastHour(UUID ecmrId) {
        return this.ecmrAssignmentRepository.countByEcmr_EcmrIdAndExternalUser_CreationTimestampGreaterThan(ecmrId, Instant.now().minusSeconds(3600));
    }

    List<EcmrAssignmentEntity> findByEcmrIdAndGroupIdAndRole(UUID ecmrId, List<Long> groupIds, EcmrRole role) {
        return this.ecmrAssignmentRepository.findByEcmr_EcmrIdAndGroup_idInAndRole(ecmrId, groupIds, role);
    }

    List<EcmrAssignmentEntity> findByExternalUser(UUID ecmrId, EcmrRole role, ExternalUser externalUser) {
        return this.ecmrAssignmentRepository.findByExternalUser(ecmrId, externalUser.getUserToken(), externalUser.getTan()).stream()
                .filter(e -> e.getRole() == role)
                .toList();
    }

}
