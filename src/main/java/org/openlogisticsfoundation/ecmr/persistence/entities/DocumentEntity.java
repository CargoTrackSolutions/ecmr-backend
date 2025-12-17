/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.entities;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.openlogisticsfoundation.ecmr.domain.models.DocumentMimeType;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "ECMR_DOCUMENTS", indexes = {
        @Index(columnList = "ecmr_id")
})
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity extends BaseEntity {
    @NotNull
    private String storageProvider;

    @Column(unique = true, nullable = false)
    private String documentId;

    @Column(name = "ecmr_id", nullable = false)
    private UUID ecmrId;

    @NotNull
    private String fileName;

    @NotNull
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant uploadDate;

    @NotNull
    private String mimeType;

    @NotNull
    private long size;
}
