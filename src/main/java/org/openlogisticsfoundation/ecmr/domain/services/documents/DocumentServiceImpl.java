/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.persistence.entities.DocumentEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class DocumentServiceImpl implements DocumentService {

    @Override
    public void uploadDocument(UUID ecmrId, MultipartFile file) throws IOException {
        DocumentEntity documentEntity = new DocumentEntity(ecmrId, file);
    }

    @Override
    public List<Document> getDocumentsByEcmrId(UUID ecmrId) {
        return List.of();
    }
}
