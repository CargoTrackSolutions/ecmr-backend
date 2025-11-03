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
import java.util.Optional;
import java.util.UUID;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.SealedDocument;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrAlreadyExistsException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.GroupNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidSealException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.RateLimitException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ShareExternallyException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrAssignmentMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.ExternalUserInformationMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.GroupPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.mappers.SealedDocumentPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.*;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUserInformationModel;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ExternalUserRegistrationCommand;
import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.openlogisticsfoundation.ecmr.domain.services.tan.PhoneMessageProvider;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrAssignmentEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.ExternalUserEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.GroupEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealedDocumentEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.UserEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrAssignmentRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.ExternalUserRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.GroupRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealedDocumentRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.UserRepository;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportWithoutUserMailModel;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportModelWithUserMail;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ecmr.seal.verify.rest.ESeal;
import ecmr.seal.verify.rest.SealVerifyResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrShareService {
    private final EcmrService ecmrService;
    private final EcmrPersistenceMapper ecmrPersistenceMapper;
    private final EcmrAssignmentRepository ecmrAssignmentRepository;
    private final ExternalUserRepository externalUserRepository;
    private final PhoneMessageProvider phoneMessageProvider;
    private final UserRepository userRepository;
    private final GroupPersistenceMapper groupPersistenceMapper;
    private final AuthorisationService authorisationService;
    private final GroupService groupService;
    private final SealedDocumentRepository sealedDocumentRepository;
    private final ExternalEcmrInstanceService externalEcmrInstanceService;
    private final SealedDocumentService sealedDocumentService;
    private final HistoryLogService historyLogService;
    private final MailService mailService;
    private final GroupRepository groupRepository;
    private final SealedDocumentPersistenceMapper sealedDocumentPersistenceMapper;
    private final MailSuffixService mailSuffixService;
    private final EcmrAssignmentMapper ecmrAssignmentMapper;
    private final ExternalUserInformationMapper externalUserInformationMapper;

    @Value("${app.origin.url}")
    private String originUrl;

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
    public String registerExternalUser(@Valid ExternalUserRegistrationCommand command)
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

        int registrationCountInLastHour = this.ecmrAssignmentRepository.countByEcmr_EcmrIdAndExternalUser_CreationTimestampGreaterThan(
                command.getEcmrId(), Instant.now().minusSeconds(3600));
        if (registrationCountInLastHour > 10) {
            throw new RateLimitException("More than 10 registrations within last hour");
        }

        String userToken = RandomStringUtils.secure().nextAlphanumeric(4);
        String tan = RandomStringUtils.secure().nextNumeric(6);
        final ExternalUserEntity externalUserEntity = this.createAndSaveExternalUser(command, userToken, tan);

        this.createAndSaveAssigment(ecmrEntity, ecmrRole, externalUserEntity);

        String sharedWith = externalUserEntity.getFirstName() + " " + externalUserEntity.getLastName() +
            " (" + externalUserEntity.getPhone() + ")";

        historyLogService.writeShareHistoryLog(ecmrEntity, ecmrEntity.getCreatedBy(), ActionType.Share_Guest, ecmrRole, sharedWith);

        String ecmrLink = this.originUrl + "/ecmr-tan/{ecmrId}/{user-token}/{tan}"
                .replace("{ecmrId}", command.getEcmrId().toString())
                .replace("{user-token}", userToken)
                .replace("{tan}", tan);
        String tanMessage = "Your tan code is " + tan + " Please enter your code or click on the following link: " + ecmrLink;
        this.phoneMessageProvider.sendMessage(command.getPhone(), tanMessage);
        return userToken;
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

    public EcmrShareResponse shareEcmrWithGroup(InternalOrExternalUser internalOrExternalUser, @Valid @NotNull UUID ecmrId,
            @Valid @NotNull Long groupId,
            @Valid @NotNull EcmrRole role) throws EcmrNotFoundException, GroupNotFoundException, NoPermissionException, ValidationException {
        ValidatedEcmrForSharing validatedEcmrForSharing = this.validateForSharingAndGetEcmr(ecmrId, role, internalOrExternalUser);

        GroupEntity groupEntity = groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));

        historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr(), internalOrExternalUser.getFullName(), ActionType.Share_Internal, role, groupEntity.getName());

        return this.shareInternally(internalOrExternalUser, role, validatedEcmrForSharing.rolesOfUSer(), groupEntity, validatedEcmrForSharing.ecmr());
    }

    public EcmrShareResponse shareEcmr(InternalOrExternalUser internalOrExternalUser, @Valid @NotNull UUID ecmrId, @Valid @NotNull String userMail,
            @Valid @NotNull EcmrRole role) throws EcmrNotFoundException, NoPermissionException, ValidationException {
        ValidatedEcmrForSharing validatedEcmrForSharing = this.validateForSharingAndGetEcmr(ecmrId, role, internalOrExternalUser);
        Optional<UserEntity> userEntityOpt = userRepository.findByEmailAndDeactivatedFalse(userMail);
        if (userEntityOpt.isPresent()) {
            UserEntity userEntity = userEntityOpt.get();
            if (userEntity.getDefaultGroup() == null) {
                return new EcmrShareResponse(ShareEcmrResult.ErrorInternalUserHasNoGroup, null, null);
            } else {

                historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr, internalOrExternalUser.getFullName(), ActionType.Share_Internal, role, userEntity.getDefaultGroup().getName());

                return this.shareInternally(internalOrExternalUser, role, validatedEcmrForSharing.rolesOfUSer(), userEntity.getDefaultGroup(),
                        validatedEcmrForSharing.ecmr());
            }
        } else {
            if (validatedEcmrForSharing.sealedDocumentEntity() == null) {
                return new EcmrShareResponse(ShareEcmrResult.ErrorSealMandatoryForExternal, null, null);
            }

            historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr, internalOrExternalUser.getFullName(), ActionType.Share_External, role, userMail);

            return this.shareExternally(ecmrId, userMail, role, validatedEcmrForSharing, internalOrExternalUser, validatedEcmrForSharing.rolesOfUSer);
        }
    }

    public String getShareToken(UUID ecmrId, EcmrRole ecmrRole, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, NoPermissionException, ValidationException {
        List<EcmrRole> rolesOfUser = authorisationService.getRolesOfUser(internalOrExternalUser, ecmrId);
        this.validateShareRoles(rolesOfUser, ecmrRole, null);
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);
        return this.getShareToken(ecmrRole, ecmrEntity);
    }

    private EcmrShareResponse shareExternally(UUID ecmrId, String userMail, EcmrRole roleToShare, ValidatedEcmrForSharing validatedEcmrForSharing,
            InternalOrExternalUser internalOrExternalUser, List<EcmrRole> rolesOfUser) {
        Optional<String> urlOpt = this.mailSuffixService.getUrl(this.extractMailDomain(userMail));
        if (urlOpt.isPresent()) {
            String url = urlOpt.get();
            if (url.equals(originUrl)) {
                return this.sendShareTokenPerEmail(ecmrId, userMail, roleToShare, validatedEcmrForSharing.ecmr(), internalOrExternalUser,
                        rolesOfUser);
            }
            if (!this.isPreviousSealPresent(roleToShare, validatedEcmrForSharing.sealedDocumentEntity())) {
                return new EcmrShareResponse(ShareEcmrResult.ErrorPreviousSealMandatoryForExternalInstance, null, null);
            }
            if (!externalEcmrInstanceService.exportEcmrMetaData(url, originUrl, ecmrId,
                    this.getShareToken(roleToShare, validatedEcmrForSharing.ecmr()),
                    userMail)) {
                return this.sendShareTokenPerEmail(ecmrId, userMail, roleToShare, validatedEcmrForSharing.ecmr(), internalOrExternalUser,
                        rolesOfUser);
            }
            if (rolesOfUser.contains(roleToShare)) {
                this.changeRoleToReadonly(internalOrExternalUser, roleToShare, ecmrId);
            }
            return new EcmrShareResponse(ShareEcmrResult.SharedExternal, null, url);
        } else {
            return this.sendShareTokenPerEmail(ecmrId, userMail, roleToShare, validatedEcmrForSharing.ecmr(), internalOrExternalUser, rolesOfUser);
        }
    }

    private ValidatedEcmrForSharing validateForSharingAndGetEcmr(UUID ecmrId, EcmrRole roleToShare, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, NoPermissionException, ValidationException {
        if (roleToShare == EcmrRole.Reader) {
            throw new ValidationException("Ecmr can't be shared to Reader");
        }
        List<EcmrRole> rolesOfUser = authorisationService.getRolesOfUser(internalOrExternalUser, ecmrId);

        SealedDocumentEntity sealedDocumentEntity = sealedDocumentService.getSealedDocumentEntity(ecmrId).orElse(null);
        this.validateShareRoles(rolesOfUser, roleToShare, sealedDocumentEntity);

        EcmrEntity ecmr;
        if (sealedDocumentEntity != null) {
            ecmr = sealedDocumentEntity.getEcmr();
        } else {
            ecmr = ecmrService.getEcmrEntity(ecmrId);
        }
        return new ValidatedEcmrForSharing(sealedDocumentEntity, ecmr, rolesOfUser);
    }

    private record ValidatedEcmrForSharing(SealedDocumentEntity sealedDocumentEntity, EcmrEntity ecmr, List<EcmrRole> rolesOfUSer) {
    }

    private EcmrShareResponse shareInternally(InternalOrExternalUser internalOrExternalUser, EcmrRole role, List<EcmrRole> rolesOfUser,
            GroupEntity groupToShareTo, EcmrEntity ecmr) {
        if (rolesOfUser.contains(role)) {
            this.changeRoleToReadonly(internalOrExternalUser, role, ecmr.getEcmrId());
        }
        List<EcmrAssignmentEntity> assignment = this.ecmrAssignmentRepository.findByEcmr_EcmrIdAndGroup_idInAndRole(
                ecmr.getEcmrId(), List.of(groupToShareTo.getId()), role);
        if (!assignment.isEmpty()) {
            return new EcmrShareResponse(ShareEcmrResult.SharedInternal, this.groupPersistenceMapper.toGroup(groupToShareTo), null);
        }

        this.createAndSaveAssigment(ecmr, role, groupToShareTo);

        return new EcmrShareResponse(ShareEcmrResult.SharedInternal, this.groupPersistenceMapper.toGroup(groupToShareTo), null);
    }

    private boolean isPreviousSealPresent(EcmrRole roleToShare, SealedDocumentEntity sealedDocument) {
        return switch (roleToShare) {
            case Carrier -> sealedDocument.getSenderSeal() != null;
            case Consignee -> sealedDocument.getCarrierSeal() != null;
            default -> false;
        };
    }

    private String extractMailDomain(String mail) {
        return mail.substring(mail.indexOf('@') + 1).toLowerCase();
    }

    private void createAndSaveAssigment(final EcmrEntity ecmr, final EcmrRole role, final ExternalUserEntity externalUser) {
        EcmrAssignmentEntity assignmentEntity = new EcmrAssignmentEntity();
        assignmentEntity.setEcmr(ecmr);
        assignmentEntity.setRole(role);
        assignmentEntity.setExternalUser(externalUser);
        ecmrAssignmentRepository.save(assignmentEntity);
    }

    private void createAndSaveAssigment(final EcmrEntity ecmr, final EcmrRole role, final GroupEntity group) {
        EcmrAssignmentEntity assignmentEntity = new EcmrAssignmentEntity();
        assignmentEntity.setEcmr(ecmr);
        assignmentEntity.setRole(role);
        assignmentEntity.setGroup(group);
        ecmrAssignmentRepository.save(assignmentEntity);
    }

    private void changeRoleToReadonly(InternalOrExternalUser internalOrExternalUser, EcmrRole roleToChange, UUID ecmrId) {
        List<EcmrAssignmentEntity> userAssignments;
        if (internalOrExternalUser.isInternalUser()) {
            List<Group> usersGroups = groupService.getGroupsForUser(internalOrExternalUser.getInternalUser().getId());
            List<Long> groupIds = groupService.flatMapGroupTrees(usersGroups).stream().map(Group::getId).toList();
            userAssignments = this.ecmrAssignmentRepository.findByEcmr_EcmrIdAndGroup_idInAndRole(ecmrId,
                    groupIds, roleToChange);
        } else {
            userAssignments = this.ecmrAssignmentRepository.findByExternalUser(
                            ecmrId, internalOrExternalUser.getExternalUser().getUserToken(), internalOrExternalUser.getExternalUser().getTan())
                    .stream().filter(e -> e.getRole() == roleToChange)
                    .toList();
        }
        userAssignments.forEach(userAssignment -> userAssignment.setRole(EcmrRole.Reader));
        ecmrAssignmentRepository.saveAll(userAssignments);
    }

    private void validateShareRoles(List<EcmrRole> userRoles, EcmrRole roleToShare, SealedDocumentEntity sealedDocument)
            throws NoPermissionException, ValidationException {
        // Check if user has necessary roles to share specific role
        if (!this.canShareRole(userRoles, roleToShare)) {
            throw new NoPermissionException("Insufficient permissions to share with " + roleToShare);
        }

        //Check if seal for roleToShare already present
        if (sealedDocument != null && this.isAlreadySealed(roleToShare, sealedDocument)) {
            throw new ValidationException(roleToShare.name() + "{} has already sealed, cant be shared with this role.");
        }
    }

    private boolean canShareRole(List<EcmrRole> userRoles, EcmrRole roleToShare) {
        return switch (roleToShare) {
            case Sender -> userRoles.contains(EcmrRole.Sender);
            case Carrier -> userRoles.contains(EcmrRole.Carrier) || userRoles.contains(EcmrRole.Sender);
            case Consignee -> userRoles.contains(EcmrRole.Carrier) || userRoles.contains(EcmrRole.Sender) || userRoles.contains(EcmrRole.Consignee);
            case Reader -> userRoles.contains(EcmrRole.Carrier) || userRoles.contains(EcmrRole.Sender) || userRoles.contains(EcmrRole.Consignee)
                    || userRoles.contains(EcmrRole.Reader);
        };
    }

    private boolean isAlreadySealed(EcmrRole roleToShare, SealedDocumentEntity sealedDocument) {
        return switch (roleToShare) {
            case Sender -> sealedDocument.getSenderSeal() != null;
            case Carrier -> sealedDocument.getCarrierSeal() != null;
            case Consignee -> sealedDocument.getConsigneeSeal() != null;
            default -> false;
        };
    }

    private String getShareToken(EcmrRole ecmrRole, EcmrEntity ecmrEntity) {
        return switch (ecmrRole) {
            case Sender -> ecmrEntity.getShareWithSenderToken();
            case Consignee -> ecmrEntity.getShareWithConsigneeToken();
            case Carrier -> ecmrEntity.getShareWithCarrierToken();
            case Reader -> ecmrEntity.getShareWithReaderToken();
        };
    }

    public EcmrModel importEcmr(AuthenticatedUser authenticatedUser, UUID ecmrId, String shareToken)
            throws EcmrNotFoundException, ValidationException, UserNotFoundException {
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);

        if (!shareToken.equals(ecmrEntity.getShareWithReaderToken())) {
            throw new ValidationException("You need the share token to import this ecmr");
        }

        UserEntity userEntity = userRepository.findById(authenticatedUser.getUser().getId())
                .orElseThrow(() -> new UserNotFoundException(authenticatedUser.getUser().getId()));

        if (userEntity.getDefaultGroup() == null) {
            throw new ValidationException("No Default Group");
        }

        this.createAndSaveAssigment(ecmrEntity, EcmrRole.Reader, userEntity.getDefaultGroup());

        return ecmrPersistenceMapper.toModel(ecmrEntity);
    }

    @Transactional
    public EcmrExportResult exportEcmrToExternal(UUID ecmrId, String shareToken) throws ValidationException {
        SealedDocumentEntity ecmrSealEntity = sealedDocumentService.getCurrentSealedDocument(ecmrId).orElseThrow();
        SealedDocument sealedDocument = sealedDocumentPersistenceMapper.toDomain(ecmrSealEntity);
        return new EcmrExportResult(sealedDocument, this.getRoleByToken(shareToken, ecmrSealEntity.getEcmr()));
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

    @Transactional
    public void importEcmrFromExternal(EcmrImportModelWithUserMail model)
            throws UserNotFoundException, ValidationException, ShareExternallyException, InvalidSealException {
        this.importEcmrFromExternal(model.getUrl(), model.getEcmrId(), model.getShareToken(), model.getUserMail());
    }

    @Transactional
    public void importEcmrFromExternal(EcmrImportWithoutUserMailModel model, AuthenticatedUser authenticatedUser)
            throws UserNotFoundException, ValidationException, ShareExternallyException, InvalidSealException {
        this.importEcmrFromExternal(model.getUrl(), model.getEcmrId(), model.getShareToken(),
                authenticatedUser.getUser().getEmail());
    }

    // import existing ecmr from external instance and save it initially on this instance
    private void importEcmrFromExternal(String url, UUID ecmrId, String shareToken, String userMail)
            throws EcmrAlreadyExistsException, ValidationException, UserNotFoundException, ShareExternallyException,
            InvalidSealException {
        // check if the ecmr is already imported
        if (ecmrService.existsByEcmrId(ecmrId)) {
            throw new EcmrAlreadyExistsException(ecmrId);
        }

        UserEntity userEntity = userRepository.findByEmailAndDeactivatedFalse(userMail)
                .orElseThrow(() -> new UserNotFoundException(userMail));
        if (userEntity.getDefaultGroup() == null) {
            throw new ValidationException("User has no Default Group");
        }

        // 1. call export endpoint from external instance
        EcmrExportResult exportResult = externalEcmrInstanceService.importEcmr(url, ecmrId, shareToken);
        // set up EcmrEntity
        SealedDocumentEntity sealedDocumentEntity = sealedDocumentPersistenceMapper.toEntity(exportResult.getSealedDocument());

        this.sealedDocumentService.validateSealedDocument(sealedDocumentEntity);

        // 2. verify sealed document before calling any internal functions
        ESeal seal = new ESeal(sealedDocumentService.getCurrentSeal(sealedDocumentEntity), null);
        if (sealedDocumentService.verify(List.of(seal)) != SealVerifyResult.VALID) {
            throw new InvalidSealException();
        }

        // 3. save ecmrSealEntity

        // create shared token for received ecmr
        sealedDocumentEntity.getEcmr().setShareWithSenderToken(RandomStringUtils.secure().nextAlphanumeric(4));
        sealedDocumentEntity.getEcmr().setShareWithCarrierToken(RandomStringUtils.secure().nextAlphanumeric(4));
        sealedDocumentEntity.getEcmr().setShareWithConsigneeToken(RandomStringUtils.secure().nextAlphanumeric(4));
        sealedDocumentEntity.getEcmr().setShareWithReaderToken(RandomStringUtils.secure().nextAlphanumeric(4));

        sealedDocumentEntity.getEcmr().setType(EcmrType.ECMR);

        sealedDocumentEntity = sealedDocumentRepository.save(sealedDocumentEntity);

        // set up group associations
        EcmrAssignmentEntity ecmrAssignmentEntity = new EcmrAssignmentEntity();
        ecmrAssignmentEntity.setEcmr(sealedDocumentEntity.getEcmr());
        ecmrAssignmentEntity.setGroup(userEntity.getDefaultGroup());
        ecmrAssignmentEntity.setRole(exportResult.getEcmrRole());
        ecmrAssignmentRepository.save(ecmrAssignmentEntity);

        // save history log
        this.historyLogService.writeHistoryLog(sealedDocumentEntity.getEcmr(), sealedDocumentEntity.getEcmr().getOriginUrl(), ActionType.Creation);
    }

    private EcmrShareResponse sendShareTokenPerEmail(UUID ecmrId, String receiverEmail, EcmrRole roleToShare, EcmrEntity ecmr,
            InternalOrExternalUser internalOrExternalUser, List<EcmrRole> rolesOfUser) {
        if (rolesOfUser.contains(roleToShare)) {
            this.changeRoleToReadonly(internalOrExternalUser, roleToShare, ecmr.getEcmrId());
        }
        String shareToken = this.getShareToken(roleToShare, ecmr);

        String shareUrl = String.format("%s/external-user-registration/%s?token=%s&role=%s", originUrl, ecmrId, shareToken, roleToShare.name());
        String mailText = """
                Sehr geehrte Damen und Herren,
                im Rahmen unseres aktuellen Transports stellen wir Ihnen hiermit den elektronischen Frachtbrief (eCMR) zur Verfügung. Über den folgenden Link können Sie das Dokument einsehen, bearbeiten und bei Bedarf digital signieren:

                {{url}}

                Wenn bei Ihnen eine eigene Instanz des eCMR Systems besteht, können Sie den eCMR auch in Ihre Instanz importieren. Melden Sie sich dazu bei Ihrer Instanz an und fügen die obige URL in den Import Dialog ein.

                Bitte beachten Sie, dass der Link aus Sicherheitsgründen nur für einen begrenzten Zeitraum gültig ist. Sollten Sie Rückfragen haben oder Unterstützung benötigen, stehen wir Ihnen selbstverständlich gerne zur Verfügung.
                Vielen Dank für die Zusammenarbeit.

                ---

                Dear Sir or Madam,
                As part of our current transport, we are providing you with the electronic consignment note (eCMR). You can view, edit, and digitally sign the document using the following link:

                {{url}}

                If you have your own instance of the eCMR system, you can also import the eCMR into your instance. To do this, log in to your instance and paste the above URL into the import dialog.

                Please note that the link is only valid for a limited time for security reasons. If you have any questions or need assistance, feel free to contact us.
                Thank you for your cooperation.
                """;
        mailService.sendMail(receiverEmail, "Import eCMR", mailText.replace("{{url}}", shareUrl));
        return new EcmrShareResponse(ShareEcmrResult.SharedExternal, null, null);
    }

    public List<EcmrAssignment> getAssignmentsOfEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser) throws NoPermissionException {
        if (authorisationService.hasNoRole(internalOrExternalUser, ecmrId)) {
            throw new NoPermissionException("No permission to load ecmr assignments");
        }
        return this.ecmrAssignmentRepository.findByEcmr_EcmrId(ecmrId).stream().map(ecmrAssignmentMapper::map).toList();
    }
}
