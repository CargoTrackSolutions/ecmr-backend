/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {

    void uploadDocument(UUID ecmrId, MultipartFile file) throws IOException;

    List<Document> getDocumentsByEcmrId(UUID ecmrId);

    void downloadDocuments(UUID ecmrId, OutputStream output);
}
