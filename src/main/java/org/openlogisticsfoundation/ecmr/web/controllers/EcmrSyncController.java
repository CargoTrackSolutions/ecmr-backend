/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidShareTokenException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.SealedDocumentNotValidException;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrSync;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/sync")
@RequiredArgsConstructor
@Log4j2
public class EcmrSyncController {

    private final EcmrSyncService ecmrSyncService;

    /**
     * Sync an eCMR from other instance
     * @param ecmrSync eCMR to sync
     * @return HttpStatus whether sync was successful
     */
    @PutMapping()
    @Operation(
            tags = "Ecmr Sync",
            summary = "Sync",
            responses = {
                    @ApiResponse(description = "Sync ecmr",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EcmrImport.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<HttpStatus> syncEcmr(@RequestBody EcmrSync ecmrSync) {
        try {
            log.info("Trying to sync ecmr {}...", ecmrSync.getEcmrId());
            ecmrSyncService.syncEcmr(ecmrSync);
            return ResponseEntity.ok(HttpStatus.OK);
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (InvalidShareTokenException | SealedDocumentNotValidException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
