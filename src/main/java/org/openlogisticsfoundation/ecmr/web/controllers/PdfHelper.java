/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.web.controllers;

import org.openlogisticsfoundation.ecmr.domain.models.PdfFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public class PdfHelper {
    private PdfHelper() {}

    static ResponseEntity<StreamingResponseBody> createPdfResponse(PdfFile pdfFile) {
        StreamingResponseBody body = pdfFile::writeTo;

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\""+ pdfFile.getFilename() + "\"")
                .body(body);
    }
}
