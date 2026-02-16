/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.entities;

import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ECMR_ASSIGNMENT", indexes = {
        @Index(name = "idx_ecmr_assignment_ecmr_id", columnList = "ecmr_id"),
        @Index(name = "idx_ecmr_assignment_group_id", columnList = "group_id"),
        @Index(name = "idx_ecmr_assignment_external_user_id", columnList = "external_user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EcmrAssignmentEntity extends BaseEntity {
    @OneToOne(optional = false)
    private EcmrEntity ecmr;
    @ManyToOne
    private GroupEntity group;
    @ManyToOne
    private ExternalUserEntity externalUser;
    @NotNull
    @Enumerated(EnumType.STRING)
    private EcmrRole role;
}
