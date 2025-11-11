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
import org.openlogisticsfoundation.ecmr.domain.exceptions.MailSuffixAlreadyExists;
import org.openlogisticsfoundation.ecmr.domain.exceptions.MailSuffixNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.models.MailSuffix;
import org.openlogisticsfoundation.ecmr.domain.models.commands.MailSuffixCommand;
import org.openlogisticsfoundation.ecmr.domain.services.MailSuffixService;
import org.openlogisticsfoundation.ecmr.web.mappers.MailSuffixWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.MailSuffixModel;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mail-suffix")
@RequiredArgsConstructor
public class MailSuffixController {

    private final MailSuffixService mailSuffixService;
    private final MailSuffixWebMapper mailSuffixWebMapper;

    /**
     * Retrieves a list of MailSuffixes associated with the provided approved URL.
     *
     * @param approvedUrlId the ID of the approved URL for which to retrieve the mail suffixes
     * @return The list of MailSuffix objects associated with the approved URL
     */
    @GetMapping("/for-approved-url/{approvedUrlId}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Mail Suffix",
            summary = "Get MailSuffixes for an approvedUrl",
            responses = {
                    @ApiResponse(description = "List of Mailsuffixes for ApprovedUrl",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = MailSuffix.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<List<MailSuffix>> getMailSuffixForApprovedUrl(@PathVariable Long approvedUrlId) {
        return ResponseEntity.ok(mailSuffixService.getMailSuffixForApprovedUrl(approvedUrlId));
    }

    /**
     * Creates a new MailSuffix.
     * @param mailSuffix The MailSuffix to create
     * @return The created MailSuffix
     */
    @PostMapping()
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Mail Suffix",
            summary = "Create a new MailSuffix",
            responses = {
                    @ApiResponse(description = "The created MailSuffix",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = MailSuffix.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access")
            }
    )
    public ResponseEntity<MailSuffix> createMailSuffix(@RequestParam Long approvedUrlId, @RequestBody MailSuffixModel mailSuffix) {
        try {
            MailSuffixCommand command = mailSuffixWebMapper.toCommand(mailSuffix);
            return ResponseEntity.ok(mailSuffixService.createMailSuffix(approvedUrlId, command));
        } catch (MailSuffixAlreadyExists e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (ApprovedUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

    }

    @PostMapping("/import/{approvedUrlId}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Mail Suffix",
            summary = "Import MailSuffixes for an ApprovedUrl",
            responses = {
                    @ApiResponse(description = "The created MailSuffixes",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = MailSuffix.class))),
            }
    )
    public ResponseEntity<List<MailSuffix>> importMailSuffixesForApprovedUrl(@PathVariable Long approvedUrlId, @RequestBody List<MailSuffixModel> mailSuffixes) {
        try {
            List<MailSuffixCommand> mailSuffixCommands = mailSuffixes.stream().map(mailSuffixWebMapper::toCommand).toList();
            return ResponseEntity.ok(mailSuffixService.importMailSuffixes(approvedUrlId, mailSuffixCommands));
        } catch (ApprovedUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Updates an existing MailSuffix with the given ID using the provided MailSuffixModel.
     *
     * @param id The ID of the MailSuffix to be updated.
     * @param mailSuffix The new MailSuffix to set to.
     * @return The updated MailSuffixed
     * @throws ResponseStatusException If the MailSuffix or ApprovedUrl is not found.
     */
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Mail Suffix",
            summary = "Update a MailSuffix",
            responses = {
                    @ApiResponse(description = "The updated MailSuffix",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = MailSuffix.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access"),
                    @ApiResponse(responseCode = "404", description = "MailSuffix or ApprovedUrl not found")
            }
    )
    public ResponseEntity<MailSuffix> updateMailSuffix(@PathVariable long id, @RequestBody String mailSuffix) {
        try {
            return ResponseEntity.ok(mailSuffixService.updateMailSuffix(id, mailSuffix));
        } catch (MailSuffixNotFoundException | ApprovedUrlNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Deletes the MailSuffix identified by the given ID.
     *
     * @param id The ID of the MailSuffix to be deleted.
     * @return Boolean indicating whether the deletion was successful.
     * @throws ResponseStatusException If the MailSuffix is not found.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated() && hasRole('Admin')")
    @Operation(
            tags = "Mail Suffix",
            summary = "Delete specific MailSuffix",
            responses = {
                    @ApiResponse(description = "Boolean indicating whether the deletion was successful",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = Boolean.class))),
                    @ApiResponse(responseCode = "401", description = "Unauthorized access"),
                    @ApiResponse(responseCode = "404", description = "MailSuffix not found")
            }
    )
    public ResponseEntity<Boolean> deleteMailSuffix(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(mailSuffixService.deleteMailSuffix(id));
        } catch (MailSuffixNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
