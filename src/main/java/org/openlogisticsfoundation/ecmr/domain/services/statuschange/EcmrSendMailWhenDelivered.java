/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.statuschange;

import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfCreationException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfaValidationException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.services.AuthorisationService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.domain.services.ExternalUserService;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Log4j2
public class EcmrSendMailWhenDelivered implements EcmrStatusChanged {

    private final EcmrShareService ecmrShareService;
    private final AuthorisationService authorisationService;
    private final ExternalUserService externalUserService;

    @Autowired
    public EcmrSendMailWhenDelivered(@Lazy EcmrShareService ecmrShareService,
                                     AuthorisationService authorisationService,
                                     ExternalUserService externalUserService) {
        this.ecmrShareService = ecmrShareService;
        this.authorisationService = authorisationService;
        this.externalUserService = externalUserService;
    }

    @Override
    public void onEcmrStatusChange(EcmrStatus previousStatus, EcmrEntity ecmrEntity, InternalOrExternalUser user) throws EcmrStatusChangedException {
        if (ecmrEntity.getEcmrStatus() != EcmrStatus.DELIVERED) {
            return;
        }

        Set<EcmrRole> ecmrRoleSet = new HashSet<>();
        List<ExternalUser> externalUsers;
        List<String> emails = new ArrayList<>();

        externalUsers = this.externalUserService.findExternalUsers(ecmrEntity.getEcmrId());

        for (ExternalUser externalUser : externalUsers) {
            List<EcmrRole> ecmrRoles = this.authorisationService.getRolesOfUser(new InternalOrExternalUser(externalUser), ecmrEntity.getEcmrId());
            ecmrRoleSet.addAll(ecmrRoles);
        }

        for (EcmrRole role : ecmrRoleSet) {
            String email = switch (role) {
                case Sender -> ecmrEntity.getSenderInformation().getEmail();
                case Carrier -> ecmrEntity.getCarrierInformation().getEmail();
                case Consignee -> ecmrEntity.getConsigneeInformation().getEmail();
                default -> null;
            };
            if (!StringUtils.isBlank(email)) emails.add(email);
        }

        try {
            this.ecmrShareService.sendPdfToExternalUsersPerEmail(ecmrEntity, user, emails);
        } catch (PdfCreationException | PdfaValidationException e) {
            log.error("Error while creating Pdf: {}", e.getMessage());
            log.debug(e);
        } catch (EcmrNotFoundException e) {
            log.error("Could not find eCMR when creating Pdf: {}", e.getMessage());
            log.debug(e);
        } catch (NoPermissionException e) {
            log.error("No permission: {}", e.getMessage());
            log.debug(e);
        }

    }
}
