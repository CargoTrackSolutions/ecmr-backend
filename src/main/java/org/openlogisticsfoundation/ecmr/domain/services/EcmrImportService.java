/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.List;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrImportNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrImportPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrImportRepository;
import org.openlogisticsfoundation.ecmr.web.models.PendingInstanceModel;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcmrImportService {

    private final EcmrImportRepository ecmrImportRepository;
    private final EcmrImportPersistenceMapper ecmrImportPersistenceMapper;
    private final ApprovedUrlService approvedUrlService;

    public List<EcmrImport> getAllEcmrImports() {
        return ecmrImportRepository.findAll().stream().map(ecmrImportPersistenceMapper::toEcmrImport).toList();
    }

    public EcmrImport saveEcmrImport(EcmrImport ecmrImport) {
        EcmrImportEntity importEntity = ecmrImportPersistenceMapper.toEcmrImportEntity(ecmrImport);
        return ecmrImportPersistenceMapper.toEcmrImport(ecmrImportRepository.save(importEntity));
    }

    public void handleApproval(AuthenticatedUser authenticatedUser, String url, Boolean approvedState)
            throws EcmrImportNotFoundException, UserNotFoundException {
        if(!approvedUrlService.existsByUrl(url)) {
            ApprovedUrlCommand newApprovedUrl = ApprovedUrlCommand.builder().approvedState(approvedState).url(url).build();
            approvedUrlService.createApprovedUrl(authenticatedUser, newApprovedUrl);
        }
    }

    public List<PendingInstanceModel> getAllPendingInstances() {
        return ecmrImportRepository.countAllGroupByInstanceUrl().stream().map(ecmrImportPersistenceMapper::toPendingInstanceModel).toList();
    }
}
