/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */


package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.openlogisticsfoundation.ecmr.web.controllers.PdfHelper.createPdfResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.NotImplementedException;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.SealMetadata;
import org.openlogisticsfoundation.ecmr.domain.exceptions.DocumentNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ExternalUserInvalidTanException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ExternalUserNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfCreationException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfaValidationException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.RateLimitException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrShareResponse;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.ExternalUserInformationModel;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.PdfFile;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrCommand;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ExternalUserRegistrationCommand;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrPdfService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrUpdateService;
import org.openlogisticsfoundation.ecmr.domain.services.ExternalUserService;
import org.openlogisticsfoundation.ecmr.domain.services.SealMetadataService;
import org.openlogisticsfoundation.ecmr.domain.services.SealService;
import org.openlogisticsfoundation.ecmr.domain.services.documents.DocumentService;
import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.openlogisticsfoundation.ecmr.web.mappers.DocumentWebMapper;
import org.openlogisticsfoundation.ecmr.web.mappers.EcmrWebMapper;
import org.openlogisticsfoundation.ecmr.web.mappers.ExternalUserWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.DocumentModel;
import org.openlogisticsfoundation.ecmr.web.models.EcmrShareModel;
import org.openlogisticsfoundation.ecmr.web.models.ExternalUserRegistrationModel;
import org.openlogisticsfoundation.ecmr.web.models.ExternalUserRegistrationResponseModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@RestController
@RequestMapping("/anonymous")
@RequiredArgsConstructor
@Log4j2
public class AnonymousController {
    private final EcmrShareService ecmrShareService;
    private final ExternalUserWebMapper externalUserWebMapper;
    private final ExternalUserService externalUserService;
    private final AuthenticationService authenticationService;
    private final EcmrService ecmrService;
    private final EcmrWebMapper ecmrWebMapper;
    private final EcmrUpdateService ecmrUpdateService;
    private final SealService ecmrSealService;
    private final EcmrPdfService ecmrPdfService;
    private final SealMetadataService sealMetadataService;
    private final DocumentService documentService;
    private final DocumentWebMapper documentWebMapper;

    /**
     * Checks if the provided TAN is valid for a given ECMR ID.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param tan The TAN to validate.
     * @return True if the TAN is valid, otherwise false.
     */
    @GetMapping("/is-tan-valid")
    @Operation(
            tags = "Anonymous",
            summary = "Check TAN Validity",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "tan", description = "TAN to validate", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "Validity of the TAN",
                            content = @Content(mediaType = "application/json", schema = @Schema(type = "boolean"))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
            })
    public ResponseEntity<Boolean> isTanValid(@RequestParam(name = "ecmrId") @Valid @NotNull UUID ecmrId,
            @RequestParam(name = "userToken") @NotNull @Valid String userToken,
            @RequestParam(name = "tan") @NotNull @Valid String tan) {
        try {
            return ResponseEntity.ok(this.externalUserService.isTanValid(ecmrId, userToken, tan));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Registers an external user.
     *
     * @param externalUserRegistrationModel The registration details of the external user.
     * @return User token
     */
    @PostMapping("/registration")
    @Operation(
            tags = "Anonymous",
            summary = "Register External User",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = ExternalUserRegistrationModel.class))),
            responses = {
                    @ApiResponse(description = "User registered successfully", responseCode = "200"),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "Validation error", responseCode = "400"),
                    @ApiResponse(description = "Internal server error", responseCode = "500")
            })
    public ResponseEntity<ExternalUserRegistrationResponseModel> registerExternalUser(
            @Valid @RequestBody ExternalUserRegistrationModel externalUserRegistrationModel) {
        try {
            ExternalUserRegistrationCommand command = externalUserWebMapper.map(externalUserRegistrationModel);
            ExternalUser externalUser = this.externalUserService.registerExternalUser(command);
            log.info("Registered external user {} for ecmr {}", externalUser, command.getEcmrId());
            return ResponseEntity.ok(new ExternalUserRegistrationResponseModel(command.getEcmrId(), externalUser.getUserToken()));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (MessageProviderException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (RateLimitException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
    }

    /**
     * Retrieves ECMR carrier details for a given ECMR ID
     *
     * @param ecmrId The UUID of the ECMR.
     * @param ecmrToken The carrier's share token of the ECMR.
     * @return the ECMR carrier information.
     */
    @GetMapping(path = { "/registration-info/{ecmrId}" })
    @Operation(
            tags = "Anonymous",
            summary = "Get ECMR carrier information",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true,
                            schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "token", description = "shareToken of the ECMR", required = true,
                            schema = @Schema(type = "string")),
            },
            responses = {
                    @ApiResponse(description = "ECMR carrier details", content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ExternalUserInformationModel.class))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "Validation error", responseCode = "400")
            })
    public ResponseEntity<ExternalUserInformationModel> getExternalUserRegistrationInfo(@PathVariable(value = "ecmrId") UUID ecmrId,
            @RequestParam(value = "token") String ecmrToken) {
        try {
            return ResponseEntity.ok(externalUserService.getRegistrationInfoFromEcmr(ecmrId, ecmrToken));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Retrieves ECMR details for a given ECMR ID.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     * @return The ECMR model.
     */
    @GetMapping(path = { "/ecmr/{ecmrId}" })
    @Operation(
            tags = "Anonymous",
            summary = "Get ECMR Details",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "ECMR details",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EcmrModel.class))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "External user not found", responseCode = "401")
            })
    public ResponseEntity<EcmrModel> getEcmrWith(@PathVariable(value = "ecmrId") UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(ecmrId, userToken, tan);
            EcmrModel ecmrModel = this.ecmrService.getEcmr(ecmrId, new InternalOrExternalUser(externalUser));
            return ResponseEntity.ok(ecmrModel);
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Updates an ECMR.
     *
     * @param tan The TAN for validation.
     * @param userToken Unique token of the external user.
     * @param ecmrModel The ECMR model to update.
     * @return The updated ECMR model.
     */
    @PutMapping("/ecmr")
    @Operation(
            tags = "Anonymous",
            summary = "Update ECMR",
            parameters = {
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = EcmrModel.class))),
            responses = {
                    @ApiResponse(description = "Updated ECMR model",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EcmrModel.class))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "External user not found", responseCode = "401")
            })
    public ResponseEntity<EcmrModel> updateEcmr(@RequestParam(name = "userToken") @Valid @NotNull String userToken,
            @RequestParam(name = "tan") @Valid @NotNull String tan,
            @RequestBody EcmrModel ecmrModel) {
        try {
            UUID ecmrId = UUID.fromString(ecmrModel.getEcmrId());
            ExternalUser externalUser = this.authenticationService.getExternalUser(ecmrId, userToken, tan);
            EcmrCommand ecmrCommand = ecmrWebMapper.toCommand(ecmrModel);
            EcmrModel result = this.ecmrUpdateService.updateEcmr(ecmrCommand, ecmrId, new InternalOrExternalUser(externalUser));
            log.info("Updated ecmr {} by external user {}", ecmrId, externalUser);
            return ResponseEntity.ok(result);
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Seals an ECMR.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     */
    @PostMapping("/ecmr/{ecmrId}/seal")
    @Operation(
            tags = "Anonymous",
            summary = "Seal ECMR",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMRto seal", required = true, schema = @Schema(type = "string", format =
                            "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "Forbidden access", responseCode = "403"),
                    @ApiResponse(description = "External user not found", responseCode = "401"),
                    @ApiResponse(description = "Validation error or seal already present", responseCode = "400")
            })
    public ResponseEntity<Void> seal(@PathVariable(value = "ecmrId") UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = this.authenticationService.getExternalUser(ecmrId, userToken, tan);
            this.ecmrSealService.sealEcmr(ecmrId, new InternalOrExternalUser(externalUser));
            log.info("Sealed ecmr {} by external user {}", ecmrId, externalUser);
            return ResponseEntity.ok().build();
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Retrieves a share token for an ECMR.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     * @param ecmrRole The role for the ECMR.
     * @return The share token.
     */
    @GetMapping("/ecmr/{ecmrId}/share-token")
    @Operation(
            tags = "Anonymous",
            summary = "Get Share Token for ECMR",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "ecmrRole", description = "Role for the ECMR", required = true, schema = @Schema(implementation = EcmrRole.class))
            },
            responses = {
                    @ApiResponse(description = "Share token",
                            content = @Content(mediaType = "application/json", schema = @Schema(type = "string"))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "External user not found", responseCode = "401"),
            })
    public ResponseEntity<String> getShareToken(@PathVariable(value = "ecmrId") UUID ecmrId, @RequestParam(name = "tan") @Valid @NotNull String tan,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "ecmrRole") @Valid @NotNull EcmrRole ecmrRole) {
        try {
            ExternalUser externalUser = this.authenticationService.getExternalUser(ecmrId, userToken, tan);
            return ResponseEntity.ok(this.ecmrShareService.getShareToken(ecmrId, ecmrRole, new InternalOrExternalUser(externalUser)));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Shares an ECMR.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     * @param ecmrShareModel The share model containing email and role.
     * @return The share response.
     */
    @PatchMapping(path = { "/ecmr/{ecmrId}/share" })
    @Operation(
            tags = "Anonymous",
            summary = "Share ECMR",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = EcmrShareModel.class))),
            responses = {
                    @ApiResponse(description = "Share response",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EcmrShareResponse.class))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "Not implemented", responseCode = "501"),
                    @ApiResponse(description = "Validation error", responseCode = "400"),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "External user not found", responseCode = "401")
            })
    public ResponseEntity<EcmrShareResponse> shareEcmr(@PathVariable(value = "ecmrId") UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan,
            @RequestBody @Valid EcmrShareModel ecmrShareModel) {
        try {
            ExternalUser externalUser = this.authenticationService.getExternalUser(ecmrId, userToken, tan);
            return ResponseEntity.ok(
                    this.ecmrShareService.shareEcmr(new InternalOrExternalUser(externalUser), ecmrId, ecmrShareModel.getEmail(),
                            ecmrShareModel.getRole()));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (NotImplementedException e) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED);
        } catch (ValidationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Downloads the ECMR PDF file.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     * @return The ECMR PDF file as a StreamingResponseBody.
     */
    @GetMapping("/ecmr/{ecmrId}/pdf")
    @Operation(
            tags = "Anonymous",
            summary = "Download ECMR PDF",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "ECMR PDF file",
                            content = @Content(mediaType = "application/pdf")),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "PDF creation error", responseCode = "500"),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "External user not found", responseCode = "401")
            })
    public ResponseEntity<StreamingResponseBody> downloadEcmrPdfFile(@PathVariable("ecmrId") UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = this.authenticationService.getExternalUser(ecmrId, userToken, tan);
            PdfFile ecmrReport = this.ecmrPdfService.createJasperReportForEcmr(ecmrId, new InternalOrExternalUser(externalUser), true, false);
            return createPdfResponse(ecmrReport);
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (PdfCreationException | PdfaValidationException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Downloads the ECMR PDF file using a share token.
     *
     * @param id The UUID of the ECMR.
     * @param shareToken The token used for sharing the ECMR.
     * @return The ECMR PDF file as a StreamingResponseBody.
     */
    @GetMapping("/ecmr/{ecmrId}/share-pdf")
    @Operation(
            tags = "Anonymous",
            summary = "Download ECMR PDF with Share Token",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "shareToken", description = "Share token for accessing the ECMR", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "ECMR PDF file",
                            content = @Content(mediaType = "application/pdf")),
                    @ApiResponse(description = "No permission", responseCode = "403"),
                    @ApiResponse(description = "PDF creation error", responseCode = "500"),
                    @ApiResponse(description = "ECMR not found", responseCode = "404")
            })
    public ResponseEntity<StreamingResponseBody> downloadEcmrPdfFileShare(@PathVariable("ecmrId") UUID id,
            @RequestParam @Valid @NotNull String shareToken) {
        try {
            PdfFile ecmrReport = this.ecmrPdfService.createJasperReportForEcmrReader(id, shareToken, true, true);
            return createPdfResponse(ecmrReport);
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (PdfCreationException | PdfaValidationException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    /**
     * Retrieves the ECMR roles for an external user.
     *
     * @param ecmrId The UUID of the ECMR.
     * @param userToken Unique token of the external user
     * @param tan The TAN for validation.
     * @return A list of ECMR roles.
     */
    @GetMapping("/ecmr-role")
    @Operation(
            tags = "Anonymous",
            summary = "Get External User ECMR Roles",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the ECMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "List of ECMR roles",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = EcmrRole.class))),
                    @ApiResponse(description = "ECMR not found", responseCode = "404"),
                    @ApiResponse(description = "External user not found", responseCode = "401")
            })
    public ResponseEntity<List<EcmrRole>> getExternalUserEcmrRoles(@RequestParam(name = "ecmrId") @Valid @NotNull UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @NotNull @Valid String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(ecmrId, userToken, tan);
            return ResponseEntity.ok(this.ecmrService.getCurrentEcmrRoles(ecmrId, new InternalOrExternalUser(externalUser)));
        } catch (EcmrNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    /**
     * Retrieves a specific sealed Document by the eCMR ID
     *
     * @param ecmrId The ID of the eCMR
     * @return The requested eCMR
     */
    @GetMapping(path = { "/ecmr/{ecmrId}/seal-metadata" })
    @Operation(
            tags = "Anonymous",
            summary = "Retrieve seal metadata by eCMR ID",
            parameters = {
                    @Parameter(name = "ecmrId", description = "UUID of the eCMR", required = true, schema = @Schema(type = "string", format = "uuid")),
                    @Parameter(name = "userToken", description = "Unique token of the external user", required = true, schema = @Schema(type = "string")),
                    @Parameter(name = "tan", description = "TAN for validation", required = true, schema = @Schema(type = "string"))
            },
            responses = {
                    @ApiResponse(description = "The requested seal metadata",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = SealMetadata.class))),
                    @ApiResponse(description = "Unauthorized access", responseCode = "401"),
                    @ApiResponse(description = "Forbidden access", responseCode = "403")
            })
    public ResponseEntity<List<SealMetadata>> getSealMetadata(@PathVariable(value = "ecmrId") UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @NotNull @Valid String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(ecmrId, userToken, tan);
            List<SealMetadata> sealMetadata = this.sealMetadataService.getSealMetadata(ecmrId, new InternalOrExternalUser(externalUser));
            return ResponseEntity.ok(sealMetadata);
        } catch (ExternalUserNotFoundException | ExternalUserInvalidTanException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping(path = "/document", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
    public ResponseEntity<Void> uploadDocumentToEcmr(@RequestParam UUID ecmrId, @RequestPart("file") @Valid @NotNull MultipartFile file,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(ecmrId, userToken, tan);
            documentService.uploadDocument(ecmrId, file, new InternalOrExternalUser(externalUser));
            return ResponseEntity.ok().build();
        } catch (IOException e) {
            log.error("Error attaching document to ECMR", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (ExternalUserInvalidTanException | ExternalUserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/document")
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
    public ResponseEntity<List<DocumentModel>> getEcmrDocuments(@RequestParam UUID ecmrId,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(ecmrId, userToken, tan);
            List<Document> documents = documentService.getDocumentsByEcmrId(ecmrId, new InternalOrExternalUser(externalUser));
            return ResponseEntity.ok(documents.stream().map(documentWebMapper::toModel).toList());
        } catch (ExternalUserInvalidTanException | ExternalUserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @GetMapping("/document/{id}/download")
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
    public ResponseEntity<InputStreamResource> downloadDocument(@PathVariable long id,
            @RequestParam(name = "userToken") @Valid @NotNull String userToken, @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(userToken, tan);
            Document document = documentService.getDocument(id, new InternalOrExternalUser(externalUser));
            InputStream fileStream = documentService.downloadDocument(id, new InternalOrExternalUser(externalUser));
            InputStreamResource inputStreamResource = new InputStreamResource(fileStream);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.getFileName() + "\"")
                    .contentLength(document.getSize())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(inputStreamResource);
        } catch (ExternalUserInvalidTanException | ExternalUserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (DocumentNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/document/{id}")
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
    public ResponseEntity<Void> deleteDocument(@PathVariable long id, @RequestParam(name = "userToken") @Valid @NotNull String userToken,
            @RequestParam(name = "tan") @Valid @NotNull String tan) {
        try {
            ExternalUser externalUser = authenticationService.getExternalUser(userToken, tan);
            documentService.deleteDocument(id, new InternalOrExternalUser(externalUser));
            return ResponseEntity.ok().build();
        } catch (ExternalUserInvalidTanException | ExternalUserNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        } catch (NoPermissionException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (DocumentNotFoundException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }
}
