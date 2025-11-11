/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.mappers;

import org.mapstruct.Mapper;
import org.openlogisticsfoundation.ecmr.domain.models.MailSuffix;
import org.openlogisticsfoundation.ecmr.domain.models.commands.MailSuffixCommand;
import org.openlogisticsfoundation.ecmr.persistence.entities.MailSuffixEntity;

@Mapper(componentModel = "spring")
public interface MailSuffixPersistenceMapper {
    MailSuffix toMailSuffix(MailSuffixEntity mailSuffixEntity);
    MailSuffixEntity toMailSuffixEntity(MailSuffixCommand mailSuffix);
}
