/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.entities;

import java.time.Instant;

import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "APPROVED_URL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApprovedUrlEntity extends BaseEntity {
    @Column(nullable = false)
    private String url;
    @ColumnDefault("false")
    private boolean approvedState;
    @Column(nullable = false, updatable = false)
    @CreationTimestamp
    private Instant creationTimestamp;
    @Column(nullable = false)
    @UpdateTimestamp
    private Instant updateTimestamp;
    @ManyToOne
    @JoinColumn(name = "last_update_user_id")
    private UserEntity lastUpdateUser;
}
