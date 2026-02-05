/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidSealException;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrExportResult;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrImportService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportCreateModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext
class ExternalControllerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EcmrShareService ecmrShareService;

    @MockitoBean
    private EcmrImportService ecmrImportService;

    @MockitoBean
    private EcmrExportResult ecmrExportResult;

    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private AuthenticatedUser authenticatedUser;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final EcmrImportCreateModel model = new EcmrImportCreateModel(UUID.randomUUID(), "test-url", "share");
    private final UUID ecmrId = UUID.randomUUID();
    private final String shareToken = "share";

    @BeforeEach
    void setUp() throws Exception {
        when(authenticationService.getAuthenticatedUser(true)).thenReturn(authenticatedUser);
    }

    // EXPORT

    // TODO
//    @Test
//    void exportEcmr_successful() throws Exception {
//        // Arrange
//        when(ecmrShareService.exportEcmrToExternal(ecmrId, shareToken)).thenReturn(ecmrExportResult);
//
//        // Act & Assert
//        mockMvc.perform(get("/external/ecmr/{ecmrId}/export", ecmrId)
//                .param("shareToken", shareToken))
//            .andExpect(status().isOk())
//            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
//            .andExpect(content().json(objectMapper.writeValueAsString(ecmrExportResult)));
//
//        verify(ecmrShareService, times(1)).exportEcmrToExternal(ecmrId, shareToken);
//    }
//
//    @Test
//    void exportEcmr_invalidShareToken() throws Exception {
//        // Arrange
//        when(ecmrShareService.exportEcmrToExternal(ecmrId, shareToken)).thenThrow(ValidationException.class);
//
//        // Act & Assert
//        mockMvc.perform(get("/external/ecmr/{ecmrId}/export", ecmrId)
//                .param("shareToken", shareToken))
//            .andExpect(status().isForbidden());
//
//        verify(ecmrShareService, times(1)).exportEcmrToExternal(ecmrId, shareToken);
//    }
//
//    @Test
//    void exportEcmr_invalidEcmrId() throws Exception {
//        // Arrange
//        when(ecmrShareService.exportEcmrToExternal(ecmrId, shareToken)).thenThrow(NoSuchElementException.class);
//
//        // Act & Assert
//        mockMvc.perform(get("/external/ecmr/{ecmrId}/export", ecmrId)
//                .param("shareToken", shareToken))
//            .andExpect(status().isNotFound());
//
//        verify(ecmrShareService, times(1)).exportEcmrToExternal(ecmrId, shareToken);
//    }

    // IMPORT

    @Test
    @WithMockUser
    void importEcmr_successful() throws Exception {
        // Act
        mockMvc.perform(post("/external/ecmr/import")
                .content(objectMapper.writeValueAsString(model)))
            .andExpect(status().isOk());

        verify(ecmrImportService, times(1)).importEcmrFromExternal(any());
    }

    @Test
    @WithMockUser
    void importEcmr_invalidSeal() throws Exception {
        // Arrange
        doThrow(InvalidSealException.class).when(ecmrImportService).importEcmrFromExternal(any());

        // Act
        mockMvc.perform(post("/external/ecmr/import")
                .content(objectMapper.writeValueAsString(model)))
            .andExpect(status().isBadRequest());

        verify(ecmrImportService, times(1)).importEcmrFromExternal(any());
    }

}
