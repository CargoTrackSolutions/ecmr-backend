/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.web.controllers;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.CountryCode;
import org.openlogisticsfoundation.ecmr.domain.models.Group;
import org.openlogisticsfoundation.ecmr.domain.models.User;
import org.openlogisticsfoundation.ecmr.domain.models.UserRole;
import org.openlogisticsfoundation.ecmr.domain.models.commands.UserCommand;
import org.openlogisticsfoundation.ecmr.domain.services.EcmrShareService;
import org.openlogisticsfoundation.ecmr.domain.services.UserService;
import org.openlogisticsfoundation.ecmr.web.mappers.UserWebMapper;
import org.openlogisticsfoundation.ecmr.web.models.UserCreationAndUpdateModel;
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

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
public class UserControllerTest {

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private EcmrShareService ecmrShareService;

    @MockitoBean
    private UserWebMapper userWebMapper;

    @MockitoBean
    private AuthenticationService authenticationService;

    private AuthenticatedUser authenticatedUser;
    private UserCommand userCommand;
    private User user;

    @BeforeEach
    public void setUp() {
        // Setup AuthenticatedUser
        authenticatedUser = mock(AuthenticatedUser.class);

        // Setup User
        user = new User(
            1L,
            "John",
            "Doe",
            CountryCode.DE,
            "john.doe@example.com",
            "123456789",
                "Example Company",
            UserRole.User,
            1L,
            false,
            false,
                false
        );
    }

    @Test
    @WithMockUser
    public void testCurrent_Success() throws Exception {
        // Arrange
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        // Act & Assert
        mockMvc.perform(get("/user/current"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        verify(authenticationService, times(1)).getAuthenticatedUser();
    }

    @Test
    @WithMockUser
    public void testGetAllUserMails_Success() throws Exception {
        // Arrange
        List<String> emails = Arrays.asList("user1@example.com", "user2@example.com");
        when(userService.getAllUserEmails()).thenReturn(emails);

        // Act & Assert
        mockMvc.perform(get("/user/mail"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.length()").value(emails.size()));

        verify(userService, times(1)).getAllUserEmails();
    }

    @Test
    @WithMockUser
    public void testGetAllUsers_Success() throws Exception {
        // Arrange
        List<User> users = Arrays.asList(user, user);
        when(userService.getAllUsers()).thenReturn(users);

        // Act & Assert
        mockMvc.perform(get("/user"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.length()").value(users.size()));

        verify(userService, times(1)).getAllUsers();
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testCreateUser_Success() throws Exception {
        // Arrange
        UserCreationAndUpdateModel userCreationAndUpdateModel = new UserCreationAndUpdateModel(UserRole.User,
            "John",
            "Doe",
            CountryCode.DE,
            "john.doe@example.com",
            "123456789",
                "Example Company",
            Arrays.asList(1L, 2L),
            1L);
        when(authenticationService.getAuthenticatedUser(true)).thenReturn(authenticatedUser);
        when(userWebMapper.toCommand(userCreationAndUpdateModel)).thenReturn(userCommand);
        when(userService.createUser(authenticatedUser, userCommand)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(post("/user")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(userCreationAndUpdateModel)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        verify(userService, times(1)).createUser(authenticatedUser, userCommand);
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testUpdateUser_Success() throws Exception {
        // Arrange
        UserCreationAndUpdateModel userCreationAndUpdateModel = new UserCreationAndUpdateModel(UserRole.User,
            "John",
            "Doe",
            CountryCode.DE,
            "john.doe@example.com",
            "123456789",
                "Example Company",
            Arrays.asList(1L, 2L),
            1L);
        long userId = 1L;
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(userWebMapper.toCommand(userCreationAndUpdateModel)).thenReturn(userCommand);
        when(userService.updateUser(authenticatedUser, userId, userCommand)).thenReturn(user);

        // Act & Assert
        mockMvc.perform(post("/user/{id}", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(userCreationAndUpdateModel)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE));

        verify(userService, times(1)).updateUser(authenticatedUser, userId, userCommand);
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testGetGroupsForUser_Success() throws Exception {
        // Arrange
        long userId = 1L;
        List<Group> groups = Arrays.asList(new Group(), new Group());
        when(userService.getGroupsByUserId(userId)).thenReturn(groups);

        // Act & Assert
        mockMvc.perform(get("/user/{id}/groups", userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.length()").value(groups.size()));

        verify(userService, times(1)).getGroupsByUserId(userId);
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testActivateUser_Success() throws Exception {
        // Arrange
        long userId = 1L;
        doNothing().when(userService).changeUserActiveState(authenticatedUser, userId, false);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        // Act & Assert
        mockMvc.perform(post("/user/{id}/activate", userId))
            .andExpect(status().isOk());

        verify(userService, times(1)).changeUserActiveState(authenticatedUser, userId, false);
    }

    @Test
    @WithMockUser(roles = "Admin")
    public void testDeactivateUser_Success() throws Exception {
        // Arrange
        long userId = 1L;
        doNothing().when(userService).changeUserActiveState(authenticatedUser, userId, true);
        when(authenticationService.getAuthenticatedUser()).thenReturn(authenticatedUser);

        // Act & Assert
        mockMvc.perform(post("/user/{id}/deactivate", userId))
            .andExpect(status().isOk());

        verify(userService, times(1)).changeUserActiveState(authenticatedUser, userId, true);
    }
}
