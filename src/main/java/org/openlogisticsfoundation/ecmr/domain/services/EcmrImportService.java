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
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.openlogisticsfoundation.ecmr.api.model.SealedDocument;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrAlreadyExistsException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrImportNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.GroupNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidSealException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ShareExternallyException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UrlNotApprovedException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrImportPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.User;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ExternalEcmrSharingCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrImportEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrImportRepository;
import org.openlogisticsfoundation.ecmr.web.models.PendingInstanceModel;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;

import ecmr.seal.verify.rest.ESeal;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcmrImportService {

    private final EcmrImportRepository ecmrImportRepository;
    private final EcmrImportPersistenceMapper ecmrImportPersistenceMapper;
    private final ApprovedUrlService approvedUrlService;
    private final SealService sealService;
    private final EcmrService ecmrService;
    private final UserService userService;
    private final EcmrCreationService ecmrCreationService;
    private final ExternalEcmrInstanceService externalEcmrInstanceService;
    private final SealMetadataService sealMetadataService;

    @PersistenceContext
    private EntityManager entityManager;

    public List<EcmrImport> getAllNotImportedEcmrImports() {
        return ecmrImportRepository.findAllByImportTimestampNull().stream().map(ecmrImportPersistenceMapper::toEcmrImport).toList();
    }

    void saveEcmrImport(EcmrImport ecmrImport) {
        EcmrImportEntity importEntity = ecmrImportPersistenceMapper.toEcmrImportEntity(ecmrImport);
        ecmrImportRepository.save(importEntity);
    }

    public void handleApproval(AuthenticatedUser authenticatedUser, String url, Boolean approvedState)
            throws EcmrImportNotFoundException, UserNotFoundException {
        if (!approvedUrlService.existsByUrl(url)) {
            ApprovedUrlCommand newApprovedUrl = ApprovedUrlCommand.builder().approvedState(approvedState).url(url).build();
            approvedUrlService.createApprovedUrl(authenticatedUser, newApprovedUrl);
        }
    }

    public List<PendingInstanceModel> getAllPendingInstances() {
        return ecmrImportRepository.countAllGroupByInstanceUrl().stream().map(ecmrImportPersistenceMapper::toPendingInstanceModel).toList();
    }

    //TODO einbauen wenn geklärt wie
    //    public void importEcmr(EcmrImportCreateCommand command, AuthenticatedUser authenticatedUser)
    //            throws UrlNotApprovedException, ShareExternallyException, InvalidSealException, EcmrAlreadyExistsException {
    //        if (this.ecmrService.existsByEcmrId(command.getEcmrId())) {
    //            throw new EcmrAlreadyExistsException(command.getEcmrId());
    //        }
    //
    //        this.checkUrlIsKnownAndApproved(command.getInstanceUrl());
    //
    //        String seal = this.externalEcmrInstanceService.importEcmr(command.getInstanceUrl(), command.getEcmrId(), command.getShareToken());
    //        if (!sealService.verify(List.of(new ESeal(seal, null)))) {
    //            throw new InvalidSealException();
    //        }
    //        EcmrImport ecmrImport = ecmrImportPersistenceMapper.toEcmrImport(command, authenticatedUser.getUser().getEmail(), seal, Instant.now());
    //        this.saveEcmrImport(ecmrImport);
    //    }

    @Transactional
    public UUID importEcmrFromExternal(ExternalEcmrSharingCommand command)
            throws ValidationException, ShareExternallyException, InvalidSealException, UserNotFoundException, UrlNotApprovedException,
            EcmrAlreadyExistsException {

        //Verify Seal
        if (!sealService.verify(this.getListOfSeals(command.getSenderSeal(), command.getCarrierSeal()))) {
            throw new InvalidSealException();
        }
        try {
            //Deserialize Seal Payload
            boolean carrierSealPresent = StringUtils.isNotBlank(command.getCarrierSeal());
            SealedDocument sealedDocument = sealService.deserializePayloadClaimJson(
                    new ESeal(carrierSealPresent ? command.getCarrierSeal() : command.getSenderSeal(), null),
                    SealedDocument.class);
            this.checkUrlIsKnownAndApproved(sealedDocument.getSealMetadata().getOriginUrl());

            //Check if user exists and has default group
            User activeUserByEmail = userService.getActiveUserByEmail(command.getReceivingUserEmail());
            if (activeUserByEmail.getDefaultGroupId() == null) {
                throw new ValidationException("User has no Default Group");
            }

            //Check if ecmr already exists
            UUID ecmrId = UUID.fromString(sealedDocument.getEcmr().getEcmrId());
            if (ecmrService.existsByEcmrId(ecmrId) || ecmrImportRepository.existsByEcmrId(ecmrId)) {
                throw new EcmrAlreadyExistsException(ecmrId);
            }

            //Save Seal in ecmr import Table
            EcmrImport ecmrImport = new EcmrImport(null, ecmrId, sealedDocument.getSealMetadata().getOriginUrl(), command.getSharingUserEmail(),
                    command.getReceivingUserEmail(), command.getShareToken(), command.getSenderSeal(), command.getCarrierSeal(), null,
                    Instant.now(), null);
            this.saveEcmrImport(ecmrImport);
            return ecmrId;
        } catch (JsonProcessingException e) {
            throw new ValidationException("No valid ecmr seal, could not extract data: " + e.getMessage());
        }
    }

    @Transactional
    public boolean importOneEcmr() {
        List<EcmrImportEntity> ecmrImportEntities = entityManager.createQuery("""
                        select e
                        from EcmrImportEntity e
                        where e.errorMessage IS null AND e.importTimestamp IS null
                        AND (e.nextRetryTimestamp IS null OR e.nextRetryTimestamp <= :currentDate )
                        order by e.creationTimestamp
                        """, EcmrImportEntity.class)
                .setParameter("currentDate", Instant.now())
                .setMaxResults(1)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (ecmrImportEntities.isEmpty()) {
            return false;
        }
        EcmrImportEntity ecmrImport = ecmrImportEntities.getFirst();
        //Check if Url is approved
        Optional<ApprovedUrl> approvedUrl = approvedUrlService.getApprovedUrl(ecmrImport.getInstanceUrl());
        if (approvedUrl.isEmpty() || !approvedUrl.get().isApprovedState()) {
            this.setErrorAndRetryState(ecmrImport, null);
            return true;
        }

        //Verify Seal
        if (!sealService.verify(getListOfSeals(ecmrImport.getSenderSeal(), ecmrImport.getCarrierSeal()))) {
            this.setErrorAndRetryState(ecmrImport, "INVALID_SEAL");
            return true;
        }

        //Deserialize Seal Payload
        boolean carrierSealPresent = StringUtils.isNotBlank(ecmrImport.getCarrierSeal());
        SealedDocument sealedDocument;
        try {
            sealedDocument = sealService.deserializePayloadClaimJson(
                    new ESeal(carrierSealPresent ? ecmrImport.getCarrierSeal() : ecmrImport.getSenderSeal(), null),
                    SealedDocument.class);
        } catch (JsonProcessingException e) {
            this.setErrorAndRetryState(ecmrImport, "INVALID_SEAL");
            return true;
        }

        if (!ecmrImport.getInstanceUrl().equals(sealedDocument.getEcmr().getEcmrId())) {
            this.setErrorAndRetryState(ecmrImport, "URL_NOT_MATCHING");
            return true;
        }

        //Check if user exists and has default group
        User activeUserByEmail;
        try {
            activeUserByEmail = userService.getActiveUserByEmail(ecmrImport.getReceivingUserEmail());
        } catch (UserNotFoundException e) {
            this.setErrorAndRetryState(ecmrImport, "USER_NOT_EXISTS");
            return true;
        }
        if (activeUserByEmail.getDefaultGroupId() == null) {
            this.setErrorAndRetryState(ecmrImport, "USER_NO_DEFAULT_GROUP");
            return true;
        }

        //Check if ecmr already exists
        UUID ecmrId = UUID.fromString(sealedDocument.getEcmr().getEcmrId());
        if (ecmrService.existsByEcmrId(ecmrId)) {
            this.setErrorAndRetryState(ecmrImport, "ECMR_ALREADY_EXISTS");
            return true;
        }

        //Save seals
        if (carrierSealPresent) {
            SealedDocument senderSealedDocument;
            try {
                senderSealedDocument = sealService.deserializePayloadClaimJson(new ESeal(ecmrImport.getSenderSeal(), null),
                        SealedDocument.class);
            } catch (JsonProcessingException e) {
                this.setErrorAndRetryState(ecmrImport, "INVALID_SEAL");
                return true;
            }

            SealMetadataEntity savedSenderSealMetadata = this.sealMetadataService.save(senderSealedDocument.getSealMetadata(), ecmrId);
            this.sealService.saveSeal(ecmrImport.getSenderSeal(), savedSenderSealMetadata);
        }

        SealMetadataEntity savedSealMetadata = this.sealMetadataService.save(sealedDocument.getSealMetadata(), ecmrId);
        this.sealService.saveSeal(carrierSealPresent ? ecmrImport.getCarrierSeal() : ecmrImport.getSenderSeal(), savedSealMetadata);

        //Create Ecmr
        try {
            this.ecmrCreationService.createEcmrFromImport(sealedDocument.getEcmr(), activeUserByEmail,
                    this.getNextRole(sealedDocument.getSealMetadata().getRole()), ecmrImport.getShareToken());
        } catch (GroupNotFoundException e) {
            this.setErrorAndRetryState(ecmrImport, "USER_GROUP_NOT_FOUND");
            return true;
        } catch (NoPermissionException e) {
            this.setErrorAndRetryState(ecmrImport, "USER_NO_PERMISSION");
            return true;
        } catch (ValidationException e) {
            this.setErrorAndRetryState(ecmrImport, "WRONG_ROLE");
            return true;
        }

        ecmrImport.setCarrierSeal(null);
        ecmrImport.setSenderSeal(null);
        ecmrImport.setImportTimestamp(Instant.now());
        ecmrImportRepository.save(ecmrImport);
        return true;
    }

    private void checkUrlIsKnownAndApproved(String urlToCheck) throws UrlNotApprovedException {
        //Check if Url is approved
        Optional<ApprovedUrl> approvedUrl = approvedUrlService.getApprovedUrl(urlToCheck);
        if (approvedUrl.isPresent() && !approvedUrl.get().isApprovedState()) {
            throw new UrlNotApprovedException(urlToCheck);
        }
    }

    private List<ESeal> getListOfSeals(String senderSeal, @Nullable String carrierSeal) {
        return StringUtils.isNotBlank(carrierSeal)
                ? List.of(new ESeal(carrierSeal, null), new ESeal(senderSeal, null))
                : List.of(new ESeal(senderSeal, null));
    }

    private EcmrRole getNextRole(TransportRole transportRole) throws ValidationException {
        if (transportRole == TransportRole.SENDER) {
            return EcmrRole.Carrier;
        } else if (transportRole == TransportRole.CARRIER) {
            return EcmrRole.Consignee;
        }
        throw new ValidationException("Wrong role shared");
    }

    private void setErrorAndRetryState(EcmrImportEntity ecmrImport, @Nullable String error) {
        Instant nextRetryDate = Instant.now().plusSeconds(5 * 60);
        ecmrImport.setNextRetryTimestamp(nextRetryDate);
        if (error != null) {
            ecmrImport.setErrorMessage(error);
        }
        ecmrImportRepository.save(ecmrImport);
    }
}
