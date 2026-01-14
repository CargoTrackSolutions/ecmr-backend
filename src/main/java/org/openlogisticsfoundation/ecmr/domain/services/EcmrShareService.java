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
import java.util.Set;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.GroupNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.GroupPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.ActionType;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrShareResponse;
import org.openlogisticsfoundation.ecmr.domain.models.Group;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.ShareEcmrResult;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrAssignmentEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.GroupEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.UserEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.GroupRepository;
import org.openlogisticsfoundation.ecmr.persistence.repositories.UserRepository;
import org.openlogisticsfoundation.ecmr.web.models.ExternalEcmrSharingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EcmrShareService {
    private final EcmrService ecmrService;
    private final EcmrAssignmentService ecmrAssignmentService;
    private final UserRepository userRepository;
    private final GroupPersistenceMapper groupPersistenceMapper;
    private final AuthorisationService authorisationService;
    private final GroupService groupService;
    private final ExternalEcmrInstanceService externalEcmrInstanceService;
    private final HistoryLogService historyLogService;
    private final MailService mailService;
    private final GroupRepository groupRepository;
    private final MailSuffixService mailSuffixService;
    private final SealMetadataService sealMetadataService;
    private final SealService sealService;

    @Value("${app.origin.url}")
    private String originUrl;

    public EcmrShareResponse shareEcmrWithGroup(InternalOrExternalUser internalOrExternalUser, @Valid @NotNull UUID ecmrId,
            @Valid @NotNull Long groupId, @Valid @NotNull EcmrRole role)
            throws EcmrNotFoundException, GroupNotFoundException, NoPermissionException, ValidationException {
        ValidatedEcmrForSharing validatedEcmrForSharing = this.validateForSharingAndGetEcmr(ecmrId, role, internalOrExternalUser);

        GroupEntity groupEntity = groupRepository.findById(groupId).orElseThrow(() -> new GroupNotFoundException(groupId));

        historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr(), internalOrExternalUser.getFullName(), ActionType.Share_Internal, role,
                groupEntity.getName());

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

                historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr, internalOrExternalUser.getFullName(), ActionType.Share_Internal,
                        role, userEntity.getDefaultGroup().getName());

                return this.shareInternally(internalOrExternalUser, role, validatedEcmrForSharing.rolesOfUSer(), userEntity.getDefaultGroup(),
                        validatedEcmrForSharing.ecmr());
            }
        } else {
            historyLogService.writeShareHistoryLog(validatedEcmrForSharing.ecmr, internalOrExternalUser.getFullName(), ActionType.Share_External,
                    role, userMail);
            return this.shareExternally(ecmrId, userMail, role, validatedEcmrForSharing, internalOrExternalUser, validatedEcmrForSharing.rolesOfUSer);
        }
    }

    // TODO wieder einbauen wenn geklärt wie
//    public String exportEcmrToExternal(UUID ecmrId, String shareToken) throws EcmrNotFoundException, ValidationException {
//        //TODO Aufrufende URL prüfen
//        EcmrEntity ecmr = ecmrService.getEcmrEntity(ecmrId);
//        EcmrRole roleOfToken = this.getRoleOfToken(shareToken, ecmr);
//        Optional<SealMetadataEntity> currentSealMetadata = sealMetadataService.getCurrentSealMetadataEntity(ecmrId);
//        if (currentSealMetadata.isEmpty()) {
//            throw new ValidationException("Ecmr not sealed");
//        }
//        if (this.isAlreadySealed(roleOfToken, currentSealMetadata.get())) {
//            throw new ValidationException("Role has already sealed");
//        }
//        if (this.isPreviousSealMissing(roleOfToken, currentSealMetadata.get())) {
//            throw new ValidationException("Previous seal missing");
//        }
//        return sealService.getSealByMetadataId(currentSealMetadata.get().getId());
//    }

    public String getShareToken(UUID ecmrId, EcmrRole ecmrRole, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, NoPermissionException, ValidationException {
        List<EcmrRole> rolesOfUser = authorisationService.getRolesOfUser(internalOrExternalUser, ecmrId);
        this.validateShareRoles(rolesOfUser, ecmrRole, null);
        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);
        return this.getShareToken(ecmrRole, ecmrEntity);
    }

    private EcmrShareResponse shareExternally(UUID ecmrId, String userMail, EcmrRole roleToShare, ValidatedEcmrForSharing validatedEcmrForSharing,
            InternalOrExternalUser internalOrExternalUser, List<EcmrRole> rolesOfUser) throws ValidationException {
        if (validatedEcmrForSharing.sealMetadata() == null || validatedEcmrForSharing.sealMetadata().isEmpty()) {
            return new EcmrShareResponse(ShareEcmrResult.ErrorSealMandatoryForExternal, null, null);
        }
        Optional<ApprovedUrl> approvedUrlOpt = this.mailSuffixService.getApprovedUrl(this.extractMailDomain(userMail));
        if (approvedUrlOpt.isPresent() && approvedUrlOpt.get().isApprovedState()) {
            String url = approvedUrlOpt.get().getUrl();
            if (url.equals(originUrl)) {
                return this.sendShareTokenPerEmail(ecmrId, userMail, roleToShare, validatedEcmrForSharing.ecmr(), internalOrExternalUser,
                        rolesOfUser);
            }
            if (this.isPreviousSealMissing(roleToShare, validatedEcmrForSharing.sealMetadata().keySet())) {
                return new EcmrShareResponse(ShareEcmrResult.ErrorPreviousSealMandatoryForExternalInstance, null, null);
            }

            if (!externalEcmrInstanceService.exportEcmrMetaData(url, this.createExternalSharingModel(roleToShare, validatedEcmrForSharing, userMail,
                    internalOrExternalUser))) {
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

    private ExternalEcmrSharingModel createExternalSharingModel(EcmrRole roleToShare, ValidatedEcmrForSharing validatedEcmrForSharing,
            String userMail, InternalOrExternalUser internalOrExternalUser) throws ValidationException {
        ExternalEcmrSharingModel externalEcmrSharingModel = new ExternalEcmrSharingModel();
        externalEcmrSharingModel.setShareToken(this.getShareToken(roleToShare, validatedEcmrForSharing.ecmr()));
        externalEcmrSharingModel.setReceivingUserEmail(userMail);
        externalEcmrSharingModel.setSharingUserEmail(
                internalOrExternalUser.isInternalUser() ? internalOrExternalUser.getInternalUser().getEmail() : null);
        try {
            externalEcmrSharingModel.setSenderSeal(
                    sealMetadataService.getSealByMetadataId(validatedEcmrForSharing.sealMetadata.get(TransportRole.SENDER).getId()));
        } catch (NullPointerException e) {
            throw new ValidationException("Sender Seal Missing");
        }

        if(validatedEcmrForSharing.sealMetadata.containsKey(TransportRole.CARRIER)) {
            externalEcmrSharingModel.setSenderSeal(
                    sealMetadataService.getSealByMetadataId(validatedEcmrForSharing.sealMetadata.get(TransportRole.CARRIER).getId()));
        }
        return externalEcmrSharingModel;
    }

    private ValidatedEcmrForSharing validateForSharingAndGetEcmr(UUID ecmrId, EcmrRole roleToShare, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, NoPermissionException, ValidationException {
        if (roleToShare == EcmrRole.Reader) {
            throw new ValidationException("Ecmr can't be shared to Reader");
        }
        List<EcmrRole> rolesOfUser = authorisationService.getRolesOfUser(internalOrExternalUser, ecmrId);

        Map<TransportRole, SealMetadataEntity> sealMetadata = sealMetadataService.getSealMetadataEntitiesMap(ecmrId, internalOrExternalUser);
        this.validateShareRoles(rolesOfUser, roleToShare, sealMetadata);

        EcmrEntity ecmr = ecmrService.getEcmrEntity(ecmrId);

        return new ValidatedEcmrForSharing(sealMetadata, ecmr, rolesOfUser);
    }

    private record ValidatedEcmrForSharing(Map<TransportRole, SealMetadataEntity> sealMetadata, EcmrEntity ecmr, List<EcmrRole> rolesOfUSer) {
    }

    private EcmrShareResponse shareInternally(InternalOrExternalUser internalOrExternalUser, EcmrRole role, List<EcmrRole> rolesOfUser,
            GroupEntity groupToShareTo, EcmrEntity ecmr) {
        if (rolesOfUser.contains(role)) {
            this.changeRoleToReadonly(internalOrExternalUser, role, ecmr.getEcmrId());
        }
        List<EcmrAssignmentEntity> assignment = this.ecmrAssignmentService.findByEcmrIdAndGroupIdAndRole(ecmr.getEcmrId(),
                List.of(groupToShareTo.getId()), role);
        if (!assignment.isEmpty()) {
            return new EcmrShareResponse(ShareEcmrResult.SharedInternal, this.groupPersistenceMapper.toGroup(groupToShareTo), null);
        }
        ecmrAssignmentService.createAndSaveAssigment(ecmr, role, groupToShareTo);
        return new EcmrShareResponse(ShareEcmrResult.SharedInternal, this.groupPersistenceMapper.toGroup(groupToShareTo), null);
    }

    private boolean isPreviousSealMissing(EcmrRole roleToShare, Set<TransportRole> sealsPresent) {
        return switch (roleToShare) {
            case Carrier -> !sealsPresent.contains(TransportRole.SENDER);
            case Consignee -> !sealsPresent.contains(TransportRole.CARRIER);
            default -> true;
        };
    }

    private String extractMailDomain(String mail) {
        return mail.substring(mail.indexOf('@') + 1).toLowerCase();
    }

    private void changeRoleToReadonly(InternalOrExternalUser internalOrExternalUser, EcmrRole roleToChange, UUID ecmrId) {
        List<EcmrAssignmentEntity> userAssignments;
        if (internalOrExternalUser.isInternalUser()) {
            List<Group> usersGroups = groupService.getGroupsForUser(internalOrExternalUser.getInternalUser().getId());
            List<Long> groupIds = groupService.flatMapGroupTrees(usersGroups).stream().map(Group::getId).toList();
            userAssignments = ecmrAssignmentService.findByEcmrIdAndGroupIdAndRole(ecmrId, groupIds, roleToChange);
        } else {
            userAssignments = ecmrAssignmentService.findByExternalUser(ecmrId, roleToChange, internalOrExternalUser.getExternalUser());
        }
        userAssignments.forEach(userAssignment -> userAssignment.setRole(EcmrRole.Reader));
        ecmrAssignmentService.saveAll(userAssignments);
    }

    private void validateShareRoles(List<EcmrRole> userRoles, EcmrRole roleToShare, Map<TransportRole, SealMetadataEntity> sealMetadata)
            throws NoPermissionException, ValidationException {
        // Check if user has necessary roles to share specific role
        if (!this.canShareRole(userRoles, roleToShare)) {
            throw new NoPermissionException("Insufficient permissions to share with " + roleToShare);
        }

        //Check if seal for roleToShare already present
        if (sealMetadata != null && !sealMetadata.isEmpty() && this.isAlreadySealed(roleToShare, sealMetadata.keySet())) {
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

    private boolean isAlreadySealed(EcmrRole roleToShare, Set<TransportRole> sealsPresent) {
        return switch (roleToShare) {
            case Sender -> sealsPresent.contains(TransportRole.SENDER);
            case Carrier -> sealsPresent.contains(TransportRole.CARRIER);
            case Consignee -> sealsPresent.contains(TransportRole.CONSIGNEE);
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

    private EcmrRole getRoleOfToken(String shareToken, EcmrEntity ecmrEntity) throws ValidationException {
        if (ecmrEntity.getShareWithSenderToken().equals(shareToken)) {
            return EcmrRole.Sender;
        } else if (ecmrEntity.getShareWithCarrierToken().equals(shareToken)) {
            return EcmrRole.Carrier;
        } else if (ecmrEntity.getShareWithConsigneeToken().equals(shareToken)) {
            return EcmrRole.Consignee;
        }
        throw new ValidationException("Invalid share token");
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
}
