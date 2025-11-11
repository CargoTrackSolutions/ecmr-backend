/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrImport;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrImportService;
import org.openlogisticsfoundation.ecmr.web.mappers.EcmrImportWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportModel;
import org.openlogisticsfoundation.ecmr.web.models.PendingInstanceModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext
class EcmrImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EcmrImportService ecmrImportService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private EcmrImportWebMapper ecmrImportWebMapper;

    private AuthenticatedUser authenticatedUser;
    private EcmrImportModel ecmrImportModel;
    private PendingInstanceModel pendingInstanceModel;

    @BeforeEach
    void setup() {
        authenticatedUser = Mockito.mock(AuthenticatedUser.class);
        ecmrImportModel = Mockito.mock(EcmrImportModel.class);
        pendingInstanceModel = Mockito.mock(PendingInstanceModel.class);
    }

    @Test
    @WithMockUser(roles = "Admin")
    void testGetImports_Success() throws Exception {
        EcmrImport ecmrImport = Mockito.mock(EcmrImport.class);
        when(ecmrImportService.getAllEcmrImports()).thenReturn(List.of(ecmrImport));
        when(ecmrImportWebMapper.toModel(ecmrImport)).thenReturn(ecmrImportModel);

        mockMvc.perform(get("/ecmr-import"))
                .andExpect(status().isOk());

        verify(ecmrImportService, times(1)).getAllEcmrImports();
        verify(ecmrImportWebMapper, times(1)).toModel(ecmrImport);
    }

    @Test
    @WithMockUser(roles = "Admin")
    void testHandleApproval_Success() throws Exception {
        String url = "https://ecmr.test.com";
        Boolean approvedState = true;

        when(authenticationService.getAuthenticatedUser(anyBoolean())).thenReturn(authenticatedUser);

        doNothing().when(ecmrImportService).handleApproval(authenticatedUser, url, approvedState);

        mockMvc.perform(put("/ecmr-import/handle-approval")
                        .param("approvedState", approvedState.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(url))
                .andExpect(status().isOk());

        verify(ecmrImportService, times(1))
                .handleApproval(authenticatedUser, url, approvedState);
    }

    @Test
    @WithMockUser(roles = "Admin")
    void testGetAllPendingInstances_Success() throws Exception {
        when(ecmrImportService.getAllPendingInstances()).thenReturn(List.of(pendingInstanceModel));

        mockMvc.perform(get("/ecmr-import/pending-instances"))
                .andExpect(status().isOk());

        verify(ecmrImportService, times(1)).getAllPendingInstances();
    }
}
