/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openlogisticsfoundation.ecmr.api.model.EcmrConsignment;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.CountryCode;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrShareResponse;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrTransportType;
import org.openlogisticsfoundation.ecmr.domain.models.Group;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.PdfFile;
import org.openlogisticsfoundation.ecmr.domain.models.ShareEcmrResult;
import org.openlogisticsfoundation.ecmr.domain.models.User;
import org.openlogisticsfoundation.ecmr.domain.models.UserRole;
import org.openlogisticsfoundation.ecmr.domain.models.commands.EcmrCommand;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrCreationService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrDeleteService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrPdfService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrUpdateService;
import org.openlogisticsfoundation.ecmr.domain.services.SealService;
import org.openlogisticsfoundation.ecmr.web.mappers.EcmrWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.BulkRequest;
import org.openlogisticsfoundation.ecmr.web.models.EcmrPageModel;
import org.openlogisticsfoundation.ecmr.web.models.EcmrShareModel;
import org.openlogisticsfoundation.ecmr.web.models.EcmrShareWithGroupModel;
import org.openlogisticsfoundation.ecmr.web.models.FilterRequestModel;
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
public class EcmrControllerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EcmrService ecmrService;

    @MockitoBean
    private EcmrUpdateService ecmrUpdateService;

    @MockitoBean
    private EcmrCreationService ecmrCreationService;

    @MockitoBean
    private EcmrPdfService ecmrPdfService;

    @MockitoBean
    private EcmrWebMapper ecmrWebMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    @MockitoBean
    private EcmrShareService ecmrShareService;

    @MockitoBean
    private EcmrDeleteService ecmrDeleteService;

    @MockitoBean
    private SealService ecmrSealService;

    private AuthenticatedUser authenticatedUser;
    private UUID ecmrId;
    private List<UUID> ecmrIds;
    private EcmrModel ecmrModel;
    private List<EcmrModel> ecmrModels;
    private EcmrCommand ecmrCommand;
    private List<Long> groupIds;
    private String jsonRequest;

    @BeforeEach
    public void setup() throws Exception {
        User user = new User(
                1L,
                "John",
                "Doe",
                CountryCode.DE,
                "john.doe@example.com",
                "123456789",
                "Example Company",
                UserRole.User,
                123L,
                false,
                false,
                false
        );
        ecmrId = UUID.randomUUID();
        ecmrModel = new EcmrModel();
        ecmrModel.setEcmrId(ecmrId.toString());
        ecmrModel.setEcmrConsignment(new EcmrConsignment());
        ecmrCommand = mock(EcmrCommand.class);
        groupIds = List.of(1L, 2L);
        jsonRequest = new ObjectMapper().writeValueAsString(ecmrModel);
        authenticatedUser = new AuthenticatedUser(user);

        // Setup test data for bulk operations
        UUID firstEcmrId = UUID.randomUUID();
        UUID secondEcmrId = UUID.randomUUID();
        ecmrIds = List.of(firstEcmrId, secondEcmrId);

        EcmrModel firstEcmrModel = new EcmrModel();
        firstEcmrModel.setEcmrId(firstEcmrId.toString());
        firstEcmrModel.setEcmrConsignment(new EcmrConsignment());

        EcmrModel secondEcmrModel = new EcmrModel();
        secondEcmrModel.setEcmrId(secondEcmrId.toString());
        secondEcmrModel.setEcmrConsignment(new EcmrConsignment());

        ecmrModels = List.of(firstEcmrModel, secondEcmrModel);

        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);
    }

    @Test
    @WithMockUser
    public void testGetMyEcmrs_Success() throws Exception {
        // Arrange
        FilterRequestModel filterRequestModel = new FilterRequestModel(
                "ecmrId",
                "referenceId",
                "from",
                "to",
                EcmrTransportType.National,
                EcmrStatus.NEW,
                "licensePlate",
                "carrierName",
                "carrierPostCode",
                "consigneePostCode",
                "lastEditor"
        );
        EcmrPageModel pageModel = new EcmrPageModel(0, 1, List.of());

        when(ecmrService.getEcmrsForUser(any(), any(), anyInt(), anyInt(), any(), any(), any())).thenReturn(pageModel);
        String filterJsonRequest = new ObjectMapper().writeValueAsString(filterRequestModel);

        // Act
        mockMvc.perform(post("/ecmr/my-ecmrs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(filterJsonRequest))
                .andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrService, times(1)).getEcmrsForUser(any(), any(), anyInt(), anyInt(), any(), any(), any());
    }

    @Test
    @WithMockUser
    public void testGetEcmr_Success() throws Exception {
        // Arrange
        when(authenticationService.getAuthenticatedUser(true)).thenReturn(authenticatedUser);
        when(ecmrService.getEcmr(eq(ecmrId), any())).thenReturn(ecmrModel);

        // Act
        mockMvc.perform(get("/ecmr/{ecmrId}", ecmrId)).andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrService, times(1)).getEcmr(eq(ecmrId), any());
    }

    @Test
    @WithMockUser(roles = "User")
    public void testCreateEcmr_Success() throws Exception {
        // Arrange
        when(authenticationService.getAuthenticatedUser(true)).thenReturn(authenticatedUser);
        when(ecmrWebMapper.toCommand(ecmrModel)).thenReturn(ecmrCommand);
        when(ecmrCreationService.createEcmr(eq(ecmrCommand), eq(authenticatedUser), any())).thenReturn(ecmrModel);

        // Act
        mockMvc.perform(post("/ecmr").param("groupId", "1,2").contentType(MediaType.APPLICATION_JSON).content(jsonRequest))
                .andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser(true);
        verify(ecmrWebMapper, times(1)).toCommand(ecmrModel);
        verify(ecmrCreationService, times(1)).createEcmr(eq(ecmrCommand), eq(authenticatedUser), eq(groupIds));
    }

    @Test
    @WithMockUser
    public void testDeleteEcmr_Success() throws Exception {
        // Act
        mockMvc.perform(delete("/ecmr/{ecmrId}", ecmrId)).andExpect(status().isNoContent());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrDeleteService, times(1)).deleteEcmr(eq(ecmrId), any());
    }

    @Test
    @WithMockUser
    public void testBulkDeleteEcmrs_Success() throws Exception {
        // Arrange
        BulkRequest bulkRequest = new BulkRequest(ecmrIds);

        String bulkRequestJson = new ObjectMapper().writeValueAsString(bulkRequest);

        // Act
        mockMvc.perform(delete("/ecmr/selected")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkRequestJson))
                .andExpect(status().isNoContent());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrDeleteService, times(1)).bulkDeleteEcmrs(eq(ecmrIds), any());
    }

    @Test
    @WithMockUser
    public void testArchiveEcmr_Success() throws Exception {
        // Arrange
        when(ecmrUpdateService.archiveEcmr(eq(ecmrId), eq(authenticatedUser))).thenReturn(ecmrModel);

        // Act
        mockMvc.perform(patch("/ecmr/{ecmrId}/archive", ecmrId)).andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrUpdateService, times(1)).archiveEcmr(eq(ecmrId), eq(authenticatedUser));
    }

    @Test
    @WithMockUser
    public void testBulkArchiveEcmrs_Success() throws Exception {
        // Arrange
        BulkRequest bulkRequest = new BulkRequest(ecmrIds);

        when(ecmrUpdateService.bulkArchiveEcmrs(eq(ecmrIds), eq(authenticatedUser))).thenReturn(ecmrModels);

        String bulkRequestJson = new ObjectMapper().writeValueAsString(bulkRequest);

        // Act
        mockMvc.perform(patch("/ecmr/archive")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bulkRequestJson))
                .andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrUpdateService, times(1)).bulkArchiveEcmrs(eq(ecmrIds), eq(authenticatedUser));
    }

    @Test
    @WithMockUser
    public void testReactivateEcmr_Success() throws Exception {
        // Arrange
        when(ecmrUpdateService.reactivateEcmr(eq(ecmrId), eq(authenticatedUser))).thenReturn(ecmrModel);

        // Act
        mockMvc.perform(patch("/ecmr/{ecmrId}/reactivate", ecmrId)).andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrUpdateService, times(1)).reactivateEcmr(eq(ecmrId), eq(authenticatedUser));
    }

    @Test
    @WithMockUser
    public void testShareEcmr_Success() throws Exception {
        //Arrange
        EcmrShareModel ecmrShareModel = new EcmrShareModel("test@example.com", EcmrRole.Carrier);
        EcmrShareResponse ecmrShareResponse = new EcmrShareResponse(ShareEcmrResult.SharedExternal, new Group(), null);

        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(ecmrShareService.shareEcmr(any(InternalOrExternalUser.class), any(UUID.class), any(String.class), any(EcmrRole.class)))
                .thenReturn(ecmrShareResponse);

        //Act
        mockMvc.perform(patch("/ecmr/{ecmrId}/share", ecmrId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(ecmrShareModel)))
                .andExpect(status().isOk());

        //Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify((ecmrShareService), times(1)).shareEcmr(any(InternalOrExternalUser.class), any(UUID.class), any(String.class), any(EcmrRole.class));
    }

    @Test
    @WithMockUser
    public void testShareEcmrWithGroup_Success() throws Exception {
        //Arrange
        EcmrShareWithGroupModel ecmrShareWithGroupModel = new EcmrShareWithGroupModel(groupIds.getFirst(), EcmrRole.Consignee);
        EcmrShareResponse ecmrShareResponse = new EcmrShareResponse(ShareEcmrResult.SharedInternal, new Group(), null);

        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(ecmrShareService.shareEcmrWithGroup(any(InternalOrExternalUser.class), any(UUID.class), any(Long.class), any(EcmrRole.class)))
                .thenReturn(ecmrShareResponse);

        //Act
        mockMvc.perform(patch("/ecmr/{ecmrId}/shareWithGroup", ecmrId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(ecmrShareWithGroupModel)))
                .andExpect(status().isOk());

        //Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify((ecmrShareService), times(1)).shareEcmrWithGroup(any(InternalOrExternalUser.class), any(UUID.class), any(Long.class),
                any(EcmrRole.class));
    }

    @Test
    @WithMockUser
    public void testDownloadEcmrPdfFile_Success() throws Exception {
        // Arrange
        String filename = "test.pdf";
        byte[] data = new byte[] { 1, 2, 3, 4, 5 };

        PdfFile pdfFile = new PdfFile(filename, data);

        when(authenticationService.getAuthenticatedUser(true)).thenReturn(authenticatedUser);
        when(ecmrPdfService.createJasperReportForEcmr(eq(ecmrId), any(InternalOrExternalUser.class), eq(true), eq(false))).thenReturn(pdfFile);

        // Act & Assert
        mockMvc.perform(get("/ecmr/{ecmrId}/pdf", ecmrId))
                .andExpect(status().isOk());

        verify(authenticationService, times(1)).getAuthenticatedUser(true);
        verify(ecmrPdfService, times(1)).createJasperReportForEcmr(eq(ecmrId), any(InternalOrExternalUser.class), eq(true), eq(false));
    }

    @Test
    @WithMockUser
    public void testUpdateEcmr_Success() throws Exception {
        // Arrange
        when(ecmrWebMapper.toCommand(ecmrModel)).thenReturn(ecmrCommand);
        when(ecmrUpdateService.updateEcmr(eq(ecmrCommand), any(), any())).thenReturn(ecmrModel);

        // Act
        mockMvc.perform(put("/ecmr").
                        contentType(MediaType.APPLICATION_JSON)
                        .content(jsonRequest))
                .andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrWebMapper, times(1)).toCommand(ecmrModel);
        verify(ecmrUpdateService, times(1)).updateEcmr(eq(ecmrCommand), any(), any());
    }

    @Test
    @WithMockUser
    public void testSeal_Success() throws Exception {
        // Arrange
        doNothing().when(ecmrSealService).sealEcmr(eq(ecmrId), any());

        // Act
        mockMvc.perform(post("/ecmr/{ecmrId}/seal", ecmrId).contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrSealService, times(1)).sealEcmr(eq(ecmrId), any());
    }

    @Test
    @WithMockUser
    public void testGetShareToken_Success() throws Exception {
        // Arrange
        EcmrRole ecmrRole = EcmrRole.Sender;
        String shareToken = "valid-share-token";

        when(ecmrShareService.getShareToken(eq(ecmrId), eq(ecmrRole), any())).thenReturn(shareToken);

        // Act
        mockMvc.perform(get("/ecmr/{ecmrId}/share-token", ecmrId).param("ecmrRole", ecmrRole.name())).andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrShareService, times(1)).getShareToken(eq(ecmrId), eq(ecmrRole), any());
    }

    @Test
    @WithMockUser
    public void testGetCurrentEcmrRoles_Success() throws Exception {
        // Arrange
        List<EcmrRole> roles = List.of(EcmrRole.Sender, EcmrRole.Consignee);

        when(ecmrService.getCurrentEcmrRoles(eq(ecmrId), any())).thenReturn(roles);

        // Act
        mockMvc.perform(get("/ecmr/{ecmrId}/role", ecmrId)).andExpect(status().isOk());

        // Assert
        verify(authenticationService, times(1)).getAuthenticatedUser();
        verify(ecmrService, times(1)).getCurrentEcmrRoles(eq(ecmrId), any());
    }
}
