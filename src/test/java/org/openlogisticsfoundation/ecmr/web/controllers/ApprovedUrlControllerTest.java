/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.openlogisticsfoundation.ecmr.domain.models.ApprovedUrl;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.commands.ApprovedUrlCommand;
import org.openlogisticsfoundation.ecmr.domain.services.ApprovedUrlService;
import org.openlogisticsfoundation.ecmr.web.mappers.ApprovedUrlWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlCreationModel;
import org.openlogisticsfoundation.ecmr.web.models.ApprovedUrlUpdateModel;
import org.openlogisticsfoundation.ecmr.web.services.AuthenticationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@DirtiesContext
public class ApprovedUrlControllerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApprovedUrlService approvedUrlService;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private ApprovedUrlCommand approvedUrlCommand;

    @MockitoBean
    private ApprovedUrlWebMapper approvedUrlWebMapper;

    private AuthenticatedUser authenticatedUser;
    private ApprovedUrl approvedUrl;
    private ApprovedUrlUpdateModel approvedUrlUpdateModel;
    private ApprovedUrlCreationModel approvedUrlCreationModel;

    @BeforeEach
    public void setup() {
        authenticatedUser = Mockito.mock(AuthenticatedUser.class);
        approvedUrl = new ApprovedUrl();
        approvedUrl.setId(1L);
        approvedUrl.setUrl("https://ecmr.test.com");

        approvedUrlCreationModel = new ApprovedUrlCreationModel("https://ecmr.test.com", true);
        approvedUrlUpdateModel = new ApprovedUrlUpdateModel(1L, "https://new.test.com", true);
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testGetAllApprovedUrls_Success() throws Exception {
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(approvedUrlService.getAllApprovedUrls()).thenReturn(Collections.singletonList(approvedUrl));

        mockMvc.perform(get("/approved-url")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testCreateApprovedUrl_Success() throws Exception {
        AuthenticatedUser authenticatedUser = Mockito.mock(AuthenticatedUser.class);

        when(authenticationService.getAuthenticatedUser(anyBoolean())).thenReturn(authenticatedUser);
        when(approvedUrlWebMapper.toCommand(any(ApprovedUrlCreationModel.class))).thenReturn(approvedUrlCommand);
        when(approvedUrlService.createApprovedUrl(any(AuthenticatedUser.class), any(ApprovedUrlCommand.class)))
                .thenReturn(approvedUrl);

        mockMvc.perform(post("/approved-url")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(approvedUrlCreationModel)))
                .andExpect(status().isOk());

        verify(approvedUrlService, times(1))
                .createApprovedUrl(any(AuthenticatedUser.class), any(ApprovedUrlCommand.class));
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testUpdateApprovedUrl_Success() throws Exception {
        when(authenticationService.getAuthenticatedUser(anyBoolean())).thenReturn(authenticatedUser);
        when(approvedUrlWebMapper.toCommand(any(ApprovedUrlUpdateModel.class))).thenReturn(approvedUrlCommand);
        when(approvedUrlService.updateApprovedUrl(any(AuthenticatedUser.class), eq(1L), any(ApprovedUrlCommand.class))).thenReturn(approvedUrl);

        mockMvc.perform(put("/approved-url/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(approvedUrlUpdateModel)))
                .andExpect(status().isOk());

        verify(approvedUrlService, times(1)).updateApprovedUrl(
                any(AuthenticatedUser.class),
                eq(1L),
                any(ApprovedUrlCommand.class)
        );
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testDeleteApprovedUrl_Success() throws Exception {
        when(approvedUrlService.deleteUrlApproval(1L)).thenReturn(true);

        mockMvc.perform(delete("/approved-url/{id}", 1L))
                .andExpect(status().isOk());

        verify(approvedUrlService, times(1)).deleteUrlApproval(1L);
    }
}
