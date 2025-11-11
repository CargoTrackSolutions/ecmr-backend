/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.mappers;

import org.mapstruct.Mapper;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlCreationModel;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlModel;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlUpdateModel;

@Mapper(componentModel = "spring")
public interface ApprovedUrlWebMapper {
    ApprovedUrlCommand toCommand(ApprovedUrlCreationModel approvedUrlCreationModel);
    ApprovedUrlCommand toCommand(ApprovedUrlUpdateModel approvedUrlUpdateModel);
    ApprovedUrlModel fromDomainToModel(ApprovedUrl approvedUrl);
}