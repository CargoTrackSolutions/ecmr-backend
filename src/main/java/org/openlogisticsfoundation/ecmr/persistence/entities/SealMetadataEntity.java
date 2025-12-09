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
import org.hibernate.annotations.UpdateTimestamp;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "SEAL_METADATA", indexes = {
    @Index(name = "idx_ecmrid", columnList = "ecmr_id"),
    @Index(name = "idx_unique_ecmrid_transportrole", columnList = "ecmr_id, transport_role", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SealMetadataEntity extends BaseEntity {
    @CreationTimestamp
    private Instant created;

    @UpdateTimestamp
    private Instant lastUpdated;

    @Column(name = "ecmr_id", nullable = false)
    private UUID ecmrId;

    @Column(nullable = false)
    private String sealer;

    @Column
    private String sealerCompany;

    @Column(name = "transport_role", nullable = false)
    @Enumerated(EnumType.STRING)
    private TransportRole role;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(nullable = false)
    private String originUrl;
}
