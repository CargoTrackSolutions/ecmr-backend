/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.web.controllers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.openlogisticsfoundation.ecmr.domain.exceptions.DocumentNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.services.documents.DocumentService;
import org.openlogisticsfoundation.ecmr.web.exceptions.AuthenticationException;
import org.openlogisticsfoundation.ecmr.web.mappers.DocumentWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.DocumentModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private final DocumentService documentService;
    private final AuthenticationService authenticationService;
    private final DocumentWebMapper documentWebMapper;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
        tags = "Document",
        summary = "Upload a document to an eCMR",
        parameters = {
            @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
            @Parameter(name = "file", description = "File to upload", required = true, content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE))
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Document uploaded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
        }
    )
    public ResponseEntity<Void> uploadDocumentToEcmr(@RequestParam UUID ecmrId, @RequestPart("file") @Valid @NotNull MultipartFile file) {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser();
            documentService.uploadDocument(ecmrId, file, authenticatedUser);
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Error attaching document to ECMR", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping
    @Operation(
        summary = "List documents of an eCMR",
        tags = "Document",
        parameters = {
            @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid"))
        },
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "List of documents",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = DocumentModel.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid ecmrId"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
        }
    )
    public ResponseEntity<List<DocumentModel>> getEcmrDocuments(@RequestParam UUID ecmrId) {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser();
            List<Document> documents = documentService.getDocumentsByEcmrId(ecmrId, authenticatedUser);
            return ResponseEntity.ok(documents.stream().map(documentWebMapper::toModel).toList());
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("{id}/download")
    @Operation(
        summary = "Download a document",
        tags = "Document",
        parameters = {
            @Parameter(name = "id", description = "Document ID", required = true, schema = @Schema(type = "long"))
        },
        responses = {
            @ApiResponse(
                responseCode = "200",
                description = "Document stream",
                content = @Content(mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE)
            ),
            @ApiResponse(responseCode = "400", description = "Invalid document id"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Document not found")
        }
    )
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable long id) {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser();
            Document document = documentService.getDocument(id, authenticatedUser);
            InputStream fileStream = documentService.downloadDocument(id, authenticatedUser);
            InputStreamResource inputStreamResource = new InputStreamResource(fileStream);
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                .contentLength(document.getSize())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(inputStreamResource);
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (DocumentNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("{id}")
    @Operation(
        summary = "Delete a document",
        tags = "Document",
        parameters = {
            @Parameter(name = "id", description = "Document ID", required = true, schema = @Schema(type = "long"))
        },
        responses = {
            @ApiResponse(responseCode = "200", description = "Document deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid document id"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden"),
            @ApiResponse(responseCode = "404", description = "Document not found")
        }
    )
    public ResponseEntity<Void> deleteDocument(@PathVariable long id) {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser();
            documentService.deleteDocument(id, authenticatedUser);
            return ResponseEntity.ok().build();
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (DocumentNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
