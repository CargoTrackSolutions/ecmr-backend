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
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.mappers.ApprovedUrlPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.ApprovedUrlEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.UserEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.ApprovedUrlRepository;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class ApprovedUrlService {

    private final ApprovedUrlRepository approvedUrlRepository;
    private final ApprovedUrlPersistenceMapper approvedUrlPersistenceMapper;
    private final UserService userService;

    public Optional<ApprovedUrl> getApprovedUrl(String url) {
        return approvedUrlRepository.findByUrl(url).map(approvedUrlPersistenceMapper::toApprovedUrl);
    }

    public List<ApprovedUrl> getAllApprovedUrls() {
        return approvedUrlRepository.findAll().stream().map(approvedUrlPersistenceMapper::toApprovedUrl).toList();
    }

    public boolean existsByUrl(String url) {
        return approvedUrlRepository.existsByUrl(url);
    }

    public ApprovedUrl createApprovedUrl(AuthenticatedUser authenticatedUser, ApprovedUrlCommand approvedUrlCommand) throws UserNotFoundException {
        ApprovedUrlEntity approvedUrlEntity = approvedUrlPersistenceMapper.toApprovedUrlEntity(approvedUrlCommand);
        UserEntity userEntity = userService.getActiveUserEntityById(authenticatedUser.getUser().getId());

        approvedUrlEntity.setLastUpdateUser(userEntity);

        return approvedUrlPersistenceMapper.toApprovedUrl(approvedUrlRepository.save(approvedUrlEntity));
    }

    public List<ApprovedUrl> createMultipleApprovedUrls(AuthenticatedUser authenticatedUser, List<ApprovedUrlCommand> approvedUrlCommands)
            throws UserNotFoundException {
        UserEntity userEntity = userService.getActiveUserEntityById(authenticatedUser.getUser().getId());
        List<ApprovedUrlEntity> mappedEntities = approvedUrlCommands.stream()
                .map(approvedUrlPersistenceMapper::toApprovedUrlEntity)
                .toList();

        List<String> urls = mappedEntities.stream()
                .map(ApprovedUrlEntity::getUrl)
                .toList();

        List<String> existingUrls = approvedUrlRepository.findAllByUrlIn(urls).stream().map(ApprovedUrlEntity::getUrl).toList();
        List<ApprovedUrlEntity> newEntities = mappedEntities.stream()
                .filter(entity -> !existingUrls.contains(entity.getUrl()))
                .map(entity -> {
                    entity.setLastUpdateUser(userEntity);
                    return entity;
                })
                .toList();

        return approvedUrlRepository.saveAll(newEntities).stream().map(approvedUrlPersistenceMapper::toApprovedUrl).toList();
    }

    public ApprovedUrl updateApprovedUrl(AuthenticatedUser authenticatedUser, Long approvedUrlId, ApprovedUrlCommand approvedUrlCommand)
            throws ApprovedUrlNotFoundException, UserNotFoundException {
        ApprovedUrlEntity approvedUrlEntity = approvedUrlRepository.findById(approvedUrlId)
                .orElseThrow(() -> new ApprovedUrlNotFoundException(approvedUrlId));
        UserEntity userEntity = userService.getActiveUserEntityById(authenticatedUser.getUser().getId());

        approvedUrlEntity.setLastUpdateUser(userEntity);
        approvedUrlEntity.setUrl(approvedUrlCommand.getUrl());
        approvedUrlEntity.setApprovedState(approvedUrlCommand.isApprovedState());

        return approvedUrlPersistenceMapper.toApprovedUrl(approvedUrlRepository.save(approvedUrlEntity));
    }

    public Boolean deleteUrlApproval(Long approvedUrlId) throws ApprovedUrlNotFoundException {
        ApprovedUrlEntity approvedUrlEntity = approvedUrlRepository.findById(approvedUrlId)
                .orElseThrow(() -> new ApprovedUrlNotFoundException(approvedUrlId));
        approvedUrlRepository.delete(approvedUrlEntity);
        return !approvedUrlRepository.existsById(approvedUrlId);
    }
}
