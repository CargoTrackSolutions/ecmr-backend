/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import java.util.List;

import org.openlogisticsfoundation.ecmr.domain.exceptions.ApprovedUrlNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.UserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.domain.services.ApprovedUrlService;
import org.openlogisticsfoundation.ecmr.web.exceptions.AuthenticationException;
import org.openlogisticsfoundation.ecmr.web.mappers.ApprovedUrlWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlCreationModel;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlUpdateModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/approved-url")
@RequiredArgsConstructor
public class ApprovedUrlController {

    private final ApprovedUrlService approvedUrlService;
    private final AuthenticationService authenticationService;
    private final ApprovedUrlWebMapper approvedUrlWebMapper;

    /**
     * Retrieves all ApprovedUrls.
     *
     * @return A list of all URLs and their approval state.
     */
    @GetMapping()
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Approved URLs",
            summary = "Get Approved URLs",
            responses = {
                    @ApiResponse(description = "List of all URLs and their approval state",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApprovedUrl.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<List<ApprovedUrl>> getApprovedUrls() {
        return ResponseEntity.ok(approvedUrlService.getAllApprovedUrls());
    }

    /**
     * Creates a new ApprovedUrl.
     * @param approvedUrl The approvedUrl Object to create
     * @return The created approvedUrl
     */
    @PostMapping()
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Approved URLs",
            summary = "Create a new approved url",
            responses = {
                    @ApiResponse(description = "The created approvedUrl",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApprovedUrl.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<ApprovedUrl> createApprovedUrl(@RequestBody ApprovedUrlCreationModel approvedUrl) throws AuthenticationException {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser(true);
            ApprovedUrlCommand command = approvedUrlWebMapper.toCommand(approvedUrl);

            return ResponseEntity.ok(approvedUrlService.createApprovedUrl(authenticatedUser, command));
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Create multiple ApprovedUrls.
     * @param approvedUrls ApprovedUrls to create
     * @return List of created ApprovedUrls
     */
    @PostMapping("/multiple")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Approved URLs",
            summary = "Create multiple ApprovedUrls",
            responses = {
                    @ApiResponse(description = "List of created ApprovedUrls",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApprovedUrl.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<List<ApprovedUrl>> createMultipleApprovedUrls(@RequestBody List<ApprovedUrlCreationModel> approvedUrls)
            throws AuthenticationException {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser(true);
            List<ApprovedUrlCommand> commandList = approvedUrls.stream().map(approvedUrlWebMapper::toCommand).toList();
            return ResponseEntity.ok(approvedUrlService.createMultipleApprovedUrls(authenticatedUser, commandList));
        } catch (UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Update an existing ApprovedUrl by Id.
     * @param id Id of ApprovedUrl to update
     * @param approvedUrl The approvedUrl to update values to
     * @return The updated ApprovedUrl
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Approved URLs",
            summary = "Update an approved url",
            responses = {
                    @ApiResponse(description = "The updated approvedUrl",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApprovedUrl.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<ApprovedUrl> updateApprovedUrl(@PathVariable long id, @RequestBody ApprovedUrlUpdateModel approvedUrl)
            throws AuthenticationException {
        try {
            AuthenticatedUser authenticatedUser = authenticationService.getAuthenticatedUser(true);
            ApprovedUrlCommand command = approvedUrlWebMapper.toCommand(approvedUrl);
            return ResponseEntity.ok(approvedUrlService.updateApprovedUrl(authenticatedUser, id, command));
        } catch (ApprovedUrlNotFoundException | UserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Deletes an approved URL.
     * @param approvedUrlId The ID of the approvedUrl Object to delete
     * @return Boolean indicating whether the deletion was successful
     */
    @DeleteMapping("/{approvedUrlId}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Approved URLs",
            summary = "Delete specific approvedUrl",
            responses = {
                    @ApiResponse(description = "Boolean indicating whether the deletion was successful",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Boolean.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access"),
                    @ApiResponse(responseCode = "404", description = "Approved URL not found")
            }
    )
    public ResponseEntity<Boolean> deleteUrlApproval(@PathVariable Long approvedUrlId) {
        try {
            return ResponseEntity.ok(approvedUrlService.deleteUrlApproval(approvedUrlId));
        } catch (ApprovedUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
