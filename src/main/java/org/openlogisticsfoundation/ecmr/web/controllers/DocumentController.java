/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.web.controllers;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.services.documents.DocumentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/document")
@RequiredArgsConstructor
@Log4j2
public class DocumentController {

    private DocumentService documentService;

    @PostMapping(path = "/upload",consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> uploadDocumentToEcmr(@RequestParam UUID ecmrId, @RequestPart("file") @Valid @NotNull MultipartFile file) {
        try {
            documentService.uploadDocument(ecmrId, file);
            return ResponseEntity.ok().build();
        } catch(IOException e) {
            log.error("Error attaching document to ECMR", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @GetMapping(path = "/ecmr/{ecmrId}")
    public ResponseEntity<List<Document>> getEcmrDocuments() {
        return ResponseEntity.ok().build();
    }
}
