/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ECMR_IMPORT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EcmrImportEntity extends BaseEntity {
    @NotNull
    @Column(name = "ecmr_id")
    private UUID ecmrId;
    @NotNull
    @Column(name = "instance_url")
    private String instanceUrl;
    @Column(name = "sharing_user_email")
    private String sharingUserEmail;
    @NotNull
    @Column(name = "receiving_user_email")
    private String receivingUserEmail;
    @NotNull
    @Column(name = "share_token")
    private String shareToken;
    @Lob
    private String senderSeal;
    @Lob
    private String carrierSeal;
    private String errorMessage;
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant creationTimestamp;
}
