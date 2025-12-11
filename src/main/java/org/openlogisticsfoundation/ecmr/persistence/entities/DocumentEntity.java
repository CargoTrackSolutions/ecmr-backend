/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.entities;

import java.sql.Timestamp;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Table(name = "ECMR_DOCUMENTS")
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class DocumentEntity extends BaseEntity {
    @Column(unique = true)
    private String documentId;

    @NotNull
    private UUID ecmrId;

    @NotNull
    private String fileName;

    @NotNull
    @CreationTimestamp
    private Timestamp uploadDate;

    @NotNull
    private String mimeType;

    @NotNull
    private int size;

    public DocumentEntity(UUID ecmrId, MultipartFile file) {
        this.ecmrId = ecmrId;
        this.fileName = file.getOriginalFilename();
        this.mimeType = file.getContentType();
        this.size = (int) file.getSize();
    }
}
