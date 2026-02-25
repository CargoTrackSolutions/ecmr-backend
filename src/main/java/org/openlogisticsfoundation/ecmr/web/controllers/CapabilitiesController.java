/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import java.util.List;

import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.services.usermanagement.ExternalUserManagement;
import org.openlogisticsfoundation.ecmr.web.capablities.BackendCapabilities;
import org.openlogisticsfoundation.ecmr.web.capablities.ExternalUserManagementFeatures;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/capabilities")
@RequiredArgsConstructor
@Log4j2
public class CapabilitiesController {

    private final List<ExternalUserManagement> externalUserManagement;

    /**
     * Returns the capabilities of backend services.
     *
     * @return A list of backend capabilities from interface implementations.
     */
    @GetMapping()
    @PreAuthorize("isAuthenticated()")
    @Operation(
            tags = "Capabilities",
            summary = "Get backend capabilities",
            responses = {
                    @ApiResponse(description = "List of backend capabilities",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = ApprovedUrl.class))),
            }
    )
    public BackendCapabilities getCapabilities() {
        ExternalUserManagementFeatures externalUserManagementFeatures =
                new ExternalUserManagementFeatures(
                        !externalUserManagement.isEmpty(),
                        !externalUserManagement.isEmpty(),
                        !externalUserManagement.isEmpty()
                );

        return new BackendCapabilities(externalUserManagementFeatures);
    }
}
