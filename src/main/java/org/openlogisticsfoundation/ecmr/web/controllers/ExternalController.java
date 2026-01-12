/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.web.controllers;

import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrAlreadyExistsException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidSealException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ShareExternallyException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UrlNotApprovedException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.ExternalEcmrSharingMapper;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ExternalEcmrSharingCommand;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrImportService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.web.models.ExternalEcmrSharingModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/external")
@RequiredArgsConstructor
@Slf4j
public class ExternalController {

    private final EcmrShareService ecmrShareService;
    private final ExternalEcmrSharingMapper externalEcmrSharingMapper;
    private final EcmrImportService ecmrImportService;

    //TODO wieder einbauen wenn geklärt wie
//    @GetMapping(path = { "/ecmr/{ecmrId}/export" })
//    @Operation(
//            tags = "ECMR External",
//            summary = "Export eCMR as sealed document with ID and share token",
//            parameters = {
//                    @Parameter(name = "ecmrId", description = "UUID of the eCMR", required = true, schema = @Schema(type = "string", format = "uuid")),
//                    @Parameter(name = "shareToken", description = "Share token", required = true, schema = @Schema(type = "string"))
//            },
//            responses = {
//                    @ApiResponse(description = "The requested ecmr as sealed document",
//                            content = @Content(
//                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
//                                    schema = @Schema(implementation = SealedDocument.class))),
//                    @ApiResponse(description = "eCMR not found", responseCode = "404"),
//                    @ApiResponse(description = "Forbidden access", responseCode = "403")
//            })
//    public ResponseEntity<String> exportEcmrToExternal(@PathVariable(value = "ecmrId") UUID ecmrId,
//            @RequestParam @Valid @NotNull String shareToken) {
//        try {
//            return ResponseEntity.ok(this.ecmrShareService.exportEcmrToExternal(ecmrId, shareToken));
//        } catch (EcmrNotFoundException e) {
//            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
//        } catch (ValidationException e) {
//            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
//        }
//    }

    @PostMapping(path = { "/ecmr/import" })
    @Operation(
            tags = "ECMR External",
            summary = "Import eCMR from seal ID, share token user emails (receiving and sharing)",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ExternalEcmrSharingModel.class))),
            responses = {
                    @ApiResponse(description = "eCMR was imported successfully", responseCode = "200"),
                    @ApiResponse(description = "Unauthorized access", responseCode = "401"),
                    @ApiResponse(description = "Forbidden access", responseCode = "403"),
                    @ApiResponse(description = "Share token is invalid", responseCode = "400"),
                    @ApiResponse(description = "User not found", responseCode = "404")
            })
    public ResponseEntity<Void> importEcmrFromExternal(@RequestBody @NotNull @Valid ExternalEcmrSharingModel model) {
        try {
            ExternalEcmrSharingCommand externalEcmrSharingCommand = externalEcmrSharingMapper.toCommand(model);
            UUID ecmrId = this.ecmrImportService.importEcmrFromExternal(externalEcmrSharingCommand);
            log.info("Added ecmr {} from external to import queue", ecmrId);
            return ResponseEntity.ok().build();
        } catch (InvalidSealException | ValidationException | ShareExternallyException | UrlNotApprovedException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (EcmrAlreadyExistsException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
