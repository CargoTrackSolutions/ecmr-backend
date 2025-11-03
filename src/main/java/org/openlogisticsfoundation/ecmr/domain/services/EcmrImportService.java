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
    private final MailSuffixService mailSuffixService;

    public List<EcmrImport> getAllEcmrImports() {
        return ecmrImportRepository.findAll().stream().map(ecmrImportPersistenceMapper::toEcmrImport).toList();
    }

    public EcmrImport saveEcmrImport(EcmrImport ecmrImport) {
        EcmrImportEntity importEntity = ecmrImportPersistenceMapper.toEcmrImportEntity(ecmrImport);
        return ecmrImportPersistenceMapper.toEcmrImport(ecmrImportRepository.save(importEntity));
    }

    public void approvePendingEcmrImport(AuthenticatedUser authenticatedUser, long ecmrImportId, Boolean addMailSuffix)
            throws EcmrImportNotFoundException, UserNotFoundException {
        EcmrImportEntity ecmrImportEntity = ecmrImportRepository.findById(ecmrImportId)
                .orElseThrow(() -> new EcmrImportNotFoundException(ecmrImportId));

        ApprovedUrlCommand newApprovedUrl = ApprovedUrlCommand.builder().approvedState(true).url(ecmrImportEntity.getInstanceUrl()).build();
        approvedUrlService.createApprovedUrl(authenticatedUser, newApprovedUrl);

        if (addMailSuffix) {
            //TODO: Add logic to add the mailSuffix of sharingUserEmail via MailSuffixService
        }
    }
}
