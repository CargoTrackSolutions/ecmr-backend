/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import java.util.List;

import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrImportNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrImportService;
import org.openlogisticsfoundation.ecmr.web.exceptions.AuthenticationException;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/ecmr-import")
@RequiredArgsConstructor
public class EcmrImportController {

    private final EcmrImportService ecmrImportService;
    private final AuthenticationService authenticationService;

    /**
     * Get all pending EcmrImports
     * @return A list of all EcmrImports
     */
    @GetMapping()
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Ecmr Imports",
            summary = "Get all pending EcmrImports",
            responses = {
                    @ApiResponse(description = "List of all pending EcmrImports",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = EcmrImport.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<List<EcmrImport>> getEcmrImports() {
        return ResponseEntity.ok(ecmrImportService.getAllEcmrImports());
    }

    /**
     * Approve a specific EcmrImport
     * @param ecmrImportId The ID of the EcmrImport to approve
     * @param addMailSuffix Whether to add the mailSuffix to DB
     * @return HttpStatus.OK when no error occurred
     * @throws AuthenticationException If not authenticated
     */
    @PutMapping("/{ecmrImportId}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Ecmr Imports",
            summary = "Approve a specific EcmrImport",
            responses = {
                    @ApiResponse(description = "Change approval of specific pending EcmrImport",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ResponseEntity.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<HttpStatus> approvePendingEcmrImport(@PathVariable Long ecmrImportId,
            @RequestParam(required = false, defaultValue = "false") boolean addMailSuffix) throws AuthenticationException {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser(true);
            ecmrImportService.approvePendingEcmrImport(authenticatedUser, ecmrImportId, addMailSuffix);

            return ResponseEntity.ok(HttpStatus.OK);
        } catch (EcmrImportNotFoundException | UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
