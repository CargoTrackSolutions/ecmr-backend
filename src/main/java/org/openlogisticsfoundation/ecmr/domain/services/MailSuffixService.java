/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.util.List;
import java.util.Optional;

import org.openlogisticsfoundation.ecmr.domain.exceptions.ApprovedUrlNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.MailSuffixAlreadyExists;
import org.openlogisticsfoundation.ecmr.domain.exceptions.MailSuffixNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.mappers.ApprovedUrlPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.MailSuffixPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.MailSuffix;
import org.openlogisticsfoundation.ecmr.domain.models.commands.MailSuffixCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.ApprovedUrlEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.MailSuffixEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.ApprovedUrlRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.MailSuffixRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class MailSuffixService {

    private final MailSuffixRepository mailSuffixRepository;
    private final ApprovedUrlPersistenceMapper approvedUrlPersistenceMapper;
    private final MailSuffixPersistenceMapper mailSuffixPersistenceMapper;
    private final ApprovedUrlRepository approvedUrlRepository;

    public Optional<ApprovedUrl> getApprovedUrl(String mailSuffix) {
        return this.mailSuffixRepository.findByMailSuffix(mailSuffix).map(MailSuffixEntity::getApprovedUrl)
                .map(approvedUrlPersistenceMapper::toApprovedUrl);
    }

    public MailSuffix createMailSuffix(Long approvedUrlId, MailSuffixCommand mailSuffixCommand)
            throws MailSuffixAlreadyExists, ApprovedUrlNotFoundException {
        if (!mailSuffixRepository.existsByMailSuffix(mailSuffixCommand.getMailSuffix())) {
            ApprovedUrlEntity approvedUrlEntity = approvedUrlRepository.findById(approvedUrlId)
                    .orElseThrow(() -> new ApprovedUrlNotFoundException(approvedUrlId));

            MailSuffixEntity mailSuffixEntity = mailSuffixPersistenceMapper.toMailSuffixEntity(mailSuffixCommand);
            mailSuffixEntity.setApprovedUrl(approvedUrlEntity);

            return mailSuffixPersistenceMapper.toMailSuffix(mailSuffixRepository.save(mailSuffixEntity));
        } else {
            throw new MailSuffixAlreadyExists(mailSuffixCommand.getMailSuffix());
        }
    }

    public List<MailSuffix> importMailSuffixes(Long approvedUrlId, List<MailSuffixCommand> mailSuffixes) throws ApprovedUrlNotFoundException {
        List<MailSuffixEntity> mappedEntities = mailSuffixes.stream()
                .map(mailSuffixPersistenceMapper::toMailSuffixEntity)
                .toList();

        List<String> suffixes = mappedEntities.stream().map(MailSuffixEntity::getMailSuffix).toList();
        List<String> existingSuffixes = mailSuffixRepository.findAllByMailSuffixIn(suffixes).stream().map(MailSuffixEntity::getMailSuffix).toList();

        ApprovedUrlEntity approvedUrlEntity = approvedUrlRepository.findById(approvedUrlId)
                .orElseThrow(() -> new ApprovedUrlNotFoundException(approvedUrlId));

        List<MailSuffixEntity> newEntities = mappedEntities.stream().
                filter(entity -> !existingSuffixes.contains(entity.getMailSuffix()))
                .map(entity -> {
                    entity.setApprovedUrl(approvedUrlEntity);
                    return entity;
                })
                .toList();

        return mailSuffixRepository.saveAll(newEntities).stream().map(mailSuffixPersistenceMapper::toMailSuffix).toList();
    }

    public List<MailSuffix> getMailSuffixForApprovedUrl(Long approvedUrlId) {
        return mailSuffixRepository.findAllByApprovedUrl_Id(approvedUrlId).stream().map(mailSuffixPersistenceMapper::toMailSuffix).toList();
    }

    public MailSuffix updateMailSuffix(Long mailSuffixId, String newMailSuffix)
            throws MailSuffixNotFoundException, ApprovedUrlNotFoundException {
        MailSuffixEntity mailSuffixEntity = mailSuffixRepository.findById(mailSuffixId)
                .orElseThrow(() -> new MailSuffixNotFoundException(mailSuffixId));

        mailSuffixEntity.setMailSuffix(newMailSuffix);

        return mailSuffixPersistenceMapper.toMailSuffix(mailSuffixRepository.save(mailSuffixEntity));
    }

    public boolean deleteMailSuffix(Long mailSuffixId) throws MailSuffixNotFoundException {
        MailSuffixEntity mailSuffixEntity = mailSuffixRepository.findById(mailSuffixId)
                .orElseThrow(() -> new MailSuffixNotFoundException(mailSuffixId));
        mailSuffixRepository.delete(mailSuffixEntity);
        return !mailSuffixRepository.existsById(mailSuffixId);
    }
}
