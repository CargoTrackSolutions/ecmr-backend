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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.openlogisticsfoundation.ecmr.api.model.SealMetadata;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.mappers.SealMetadataPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealMetadataRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealRepository;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@AllArgsConstructor
@Log4j2
public class SealMetadataService {
    private final SealMetadataRepository sealMetadataRepository;
    private final AuthorisationService authorisationService;
    private final SealMetadataPersistenceMapper sealMetadataPersistenceMapper;
    private final SealRepository sealRepository;

    public boolean sealExists(UUID ecmrId) {
        return sealMetadataRepository.existsByEcmrId(ecmrId);
    }

    public List<SealMetadata> getSealMetadata(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws NoPermissionException {
        return getSealMetadataEntities(ecmrId, internalOrExternalUser).stream()
                .map(sealMetadataPersistenceMapper::toDomain)
                .toList();
    }

    public String getSealByMetadataId(long id) {
        //Throw an IllegalStateException when no seal was found for this sealmetadata.id
        return sealRepository.findByMetadataId(id).orElseThrow(() -> new IllegalStateException("No seal found for seal metadata id " + id)).getSeal();
    }

    public List<SealMetadataEntity> getSealMetadataEntities(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws NoPermissionException {
        if (authorisationService.hasNoRole(internalOrExternalUser, ecmrId)) {
            throw new NoPermissionException("No permission to load seal metadata");
        }
        this.getSealMetadataEntities(ecmrId);
        return this.getSealMetadataEntities(ecmrId);
    }

    List<SealMetadata> getSealMetadata(UUID ecmrId) {
        return sealMetadataRepository.findByEcmrId(ecmrId).stream()
                .map(sealMetadataPersistenceMapper::toDomain)
                .toList();
    }

    Map<TransportRole, SealMetadataEntity> getSealMetadataEntitiesMap(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws NoPermissionException {
        if (authorisationService.hasNoRole(internalOrExternalUser, ecmrId)) {
            throw new NoPermissionException("No permission to load seal metadata");
        }
        List<SealMetadataEntity> sealMetadataEntities = this.getSealMetadataEntities(ecmrId);
        return sealMetadataEntities.stream().collect(Collectors.toMap(SealMetadataEntity::getRole, Function.identity()));
    }

    List<SealMetadataEntity> getSealMetadataEntities(UUID ecmrId) {
        return sealMetadataRepository.findByEcmrId(ecmrId);
    }

    Optional<SealMetadataEntity> getCurrentSealMetadataEntity(UUID ecmrId, InternalOrExternalUser internalOrExternalUser)
            throws NoPermissionException {
        List<SealMetadataEntity> sealMetadataEntities = getSealMetadataEntities(ecmrId, internalOrExternalUser);
        return getCurrentSealMetadataEntity(sealMetadataEntities);
    }

    private Optional<SealMetadataEntity> getCurrentSealMetadataEntity(List<SealMetadataEntity> sealMetadataEntities) {
        Optional<SealMetadataEntity> consigneeSeal = sealMetadataEntities.stream().filter(x -> x.getRole() == TransportRole.CONSIGNEE).findFirst();
        Optional<SealMetadataEntity> carrierSeal = sealMetadataEntities.stream().filter(x -> x.getRole() == TransportRole.CARRIER).findFirst();
        Optional<SealMetadataEntity> senderSeal = sealMetadataEntities.stream().filter(x -> x.getRole() == TransportRole.SENDER).findFirst();
        if (consigneeSeal.isPresent()) {
            return consigneeSeal;
        } else if (carrierSeal.isPresent()) {
            return carrierSeal;
        } else if (senderSeal.isPresent()) {
            return senderSeal;
        }
        return Optional.empty();
    }

    SealMetadataEntity save(SealMetadata sealMetadata, UUID ecmrId) {
        SealMetadataEntity sealMetadataEntity = sealMetadataPersistenceMapper.toEntity(sealMetadata, ecmrId);
        return sealMetadataRepository.save(sealMetadataEntity);
    }
}
