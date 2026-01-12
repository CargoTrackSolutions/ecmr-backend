/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ExternalUserInvalidTanException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ExternalUserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.RateLimitException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.ExternalUserInformationMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.ExternalUserPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUserInformationModel;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ExternalUserRegistrationCommand;
import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.openlogisticsfoundation.ecmr.domain.services.tan.PhoneMessageProvider;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.ExternalUserEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.ExternalUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class ExternalUserService {
    private final EcmrService ecmrService;
    private final AuthorisationService authorisationService;
    private final ExternalUserPersistenceMapper externalUserPersistenceMapper;
    private final ExternalUserRepository externalUserRepository;
    private final PhoneMessageProvider phoneMessageProvider;
    private final ExternalUserInformationMapper externalUserInformationMapper;
    private final EcmrAssignmentService ecmrAssignmentService;
    private final static int MAXIMUM_INVALID_TAN_COUNT = 10;

    @Value("${app.origin.url}")
    private String originUrl;

    public ExternalUser findExternalUser(UUID ecmrId, String userToken, String tan)
            throws ExternalUserNotFoundException, ExternalUserInvalidTanException {
        ExternalUserEntity externalUser = this.externalUserRepository.findExtenalUserByUserTokenAndEcmrId(userToken, ecmrId)
                .orElseThrow(() -> new ExternalUserNotFoundException(userToken));

        if (!Objects.equals(externalUser.getTan(), tan)) {
            externalUser.setInvalidLoginCount(externalUser.getInvalidLoginCount() + 1);
            externalUser.setActive(externalUser.isActive() && externalUser.getInvalidLoginCount() < MAXIMUM_INVALID_TAN_COUNT);
            externalUserRepository.save(externalUser);
            throw new ExternalUserInvalidTanException(userToken);
        }

        return externalUserPersistenceMapper.toDomain(externalUser);
    }

    public boolean isTanValid(@Valid @NotNull UUID ecmrId, @Valid @NotNull String userToken, @Valid @NotNull String tan)
            throws EcmrNotFoundException {
        if (!this.ecmrService.existsByEcmrId(ecmrId)) {
            throw new EcmrNotFoundException(ecmrId);
        }
        return authorisationService.tanValid(ecmrId, userToken, tan);
    }

    public ExternalUserInformationModel getRegistrationInfoFromEcmr(UUID ecmrId, String ecmrToken) throws EcmrNotFoundException,
            ValidationException {
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);

        EcmrRole roleByToken = this.getRoleByToken(ecmrToken, ecmrEntity);

        if (roleByToken == EcmrRole.Sender) {
            return externalUserInformationMapper.mapSenderData(ecmrEntity);
        } else if (roleByToken == EcmrRole.Carrier) {
            return externalUserInformationMapper.mapCarrierData(ecmrEntity);
        } else if (roleByToken == EcmrRole.Consignee) {
            return externalUserInformationMapper.mapConsigneeData(ecmrEntity);
        } else {
            throw new ValidationException("Only sender, carriers and consignees can register external users");
        }
    }

    ///  Return the user token
    public ExternalUser registerExternalUser(@Valid ExternalUserRegistrationCommand command)
            throws EcmrNotFoundException, ValidationException, MessageProviderException, RateLimitException {
        if (StringUtils.isBlank(command.getPhone())) {
            // Currently only phone is supported. Sharing an ecmr via e-mail could be a security risk
            throw new ValidationException("Phone must be filled");
        }
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(command.getEcmrId());
        EcmrRole ecmrRole = this.getRoleByToken(command.getShareToken(), ecmrEntity);

        List<ExternalUserEntity> existingExternalUsersByPhone = this.externalUserRepository.findByPhone(
                command.getPhone()); //Deactivate all other tans for this phone
        for (ExternalUserEntity externalUserEntity : existingExternalUsersByPhone) {
            externalUserEntity.setActive(false);
            externalUserRepository.save(externalUserEntity);
        }

        int registrationCountInLastHour = ecmrAssignmentService.getRegistrationCountInLastHour(command.getEcmrId());
        if (registrationCountInLastHour > 10) {
            throw new RateLimitException("More than 10 registrations within last hour");
        }

        String userToken = RandomStringUtils.secure().nextAlphanumeric(4);
        String tan = RandomStringUtils.secure().nextNumeric(6);
        final ExternalUserEntity externalUserEntity = this.createAndSaveExternalUser(command, userToken, tan);

        ecmrAssignmentService.createAndSaveAssigment(ecmrEntity, ecmrRole, externalUserEntity);

        String ecmrLink = this.originUrl + "/ecmr-tan/{ecmrId}/{user-token}/{tan}"
                .replace("{ecmrId}", command.getEcmrId().toString())
                .replace("{user-token}", userToken)
                .replace("{tan}", tan);
        String tanMessage = "Your tan code is " + tan + " Please enter your code or click on the following link: " + ecmrLink;
        this.phoneMessageProvider.sendMessage(command.getPhone(), tanMessage);
        return externalUserPersistenceMapper.toDomain(externalUserEntity);
    }

    private ExternalUserEntity createAndSaveExternalUser(final ExternalUserRegistrationCommand command, final String userToken, final String tan) {
        Instant tanValidUntil = Instant.now().plus(365, ChronoUnit.DAYS);
        ExternalUserEntity externalUserEntity = new ExternalUserEntity();
        externalUserEntity.setFirstName(command.getFirstName());
        externalUserEntity.setLastName(command.getLastName());
        externalUserEntity.setCompany(command.getCompany());
        externalUserEntity.setPhone(command.getPhone());
        externalUserEntity.setEmail(command.getEmail());
        externalUserEntity.setUserToken(userToken);
        externalUserEntity.setTan(tan);
        externalUserEntity.setTanValidUntil(tanValidUntil);
        externalUserEntity.setCreationTimestamp(Instant.now());
        externalUserEntity.setActive(true);
        return externalUserRepository.save(externalUserEntity);
    }

    private EcmrRole getRoleByToken(String shareToken, EcmrEntity ecmr) throws ValidationException {
        if (shareToken.equals(ecmr.getShareWithSenderToken())) {
            return EcmrRole.Sender;
        } else if (shareToken.equals(ecmr.getShareWithCarrierToken())) {
            return EcmrRole.Carrier;
        } else if (shareToken.equals(ecmr.getShareWithConsigneeToken())) {
            return EcmrRole.Consignee;
        } else if (shareToken.equals(ecmr.getShareWithReaderToken())) {
            return EcmrRole.Reader;
        } else { // share token not valid
            throw new ValidationException("No valid share token.");
        }
    }
}
