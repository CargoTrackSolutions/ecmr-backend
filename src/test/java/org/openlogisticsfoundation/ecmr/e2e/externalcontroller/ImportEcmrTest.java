/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.e2e.externalcontroller;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.junit.jupiter.api.Test;
import org.openlogisticsfoundation.ecmr.domain.services.SealDnsFingerprintVerificationService;
import org.openlogisticsfoundation.ecmr.e2e.E2EBaseTest;
import org.openlogisticsfoundation.ecmr.e2e.ResourceLoader;
import org.openlogisticsfoundation.ecmr.web.models.EcmrImportModel;
import org.openlogisticsfoundation.ecmr.web.models.ExternalEcmrSharingModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.xbill.DNS.TextParseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import eu.europa.esig.dss.jades.JWSCompactSerializationParser;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.InMemoryDocument;
import io.restassured.common.mapper.TypeRef;
import io.restassured.response.Response;

class ImportEcmrTest extends E2EBaseTest {

    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @MockitoBean
    SealDnsFingerprintVerificationService sealDnsFingerprintVerificationService;

    @Test
    void importEcmr_invalidDNSValidation() throws Exception {
        ExternalEcmrSharingModel externalEcmrSharingModel = objectMapper.readValue(
                ResourceLoader.load("/json-objects/external-ecmr-sharing-model-invalid.json"), ExternalEcmrSharingModel.class);

        given()
                .accept(String.valueOf(MediaType.APPLICATION_JSON))
                .header("Authorization", "Bearer " + adminToken)
                .port(randomServerPort)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(externalEcmrSharingModel))
                .when()
                .post("/api/external/ecmr/import")

                .then()
                .statusCode(400);
    }

    @Test
    void importEcmr_invalidSeal() throws Exception {
        ExternalEcmrSharingModel externalEcmrSharingModel = objectMapper.readValue(
                ResourceLoader.load("/json-objects/external-ecmr-sharing-model-invalid.json"), ExternalEcmrSharingModel.class);
        this.mockCertDnsValidation(externalEcmrSharingModel.getSenderSeal());

        given()
                .accept(String.valueOf(MediaType.APPLICATION_JSON))
                .header("Authorization", "Bearer " + adminToken)
                .port(randomServerPort)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(objectMapper.writeValueAsString(externalEcmrSharingModel))
                .when()
                .post("/api/external/ecmr/import")

                .then()
                .statusCode(400);
    }

    @Test
    void importEcmr_invalidUserMail() throws Exception {
        ExternalEcmrSharingModel externalEcmrSharingModel = objectMapper.readValue(
                ResourceLoader.load("/json-objects/external-ecmr-sharing-model.json"), ExternalEcmrSharingModel.class);
        externalEcmrSharingModel.setReceivingUserEmail("non-existing-user@test.de");
        this.mockCertDnsValidation(externalEcmrSharingModel.getSenderSeal());

        given()
                .accept(String.valueOf(MediaType.APPLICATION_JSON))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + adminToken)
                .body(objectMapper.writeValueAsString(externalEcmrSharingModel))
                .port(randomServerPort)

                .when()
                .post("/api/external/ecmr/import")

                .then()
                .statusCode(404);
    }

    @Test
    void importEcmr_valid() throws Exception {
        ExternalEcmrSharingModel externalEcmrSharingModel = objectMapper.readValue(
                ResourceLoader.load("/json-objects/external-ecmr-sharing-model.json"), ExternalEcmrSharingModel.class);
        this.mockCertDnsValidation(externalEcmrSharingModel.getSenderSeal());

        given()
                .accept(String.valueOf(MediaType.APPLICATION_JSON))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + adminToken)
                .body(objectMapper.writeValueAsString(externalEcmrSharingModel))
                .port(randomServerPort)

                .when()
                .post("/api/external/ecmr/import")

                .then()
                .statusCode(200);

        Response response = given()
                .accept(String.valueOf(MediaType.APPLICATION_JSON))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .header("Authorization", "Bearer " + adminToken)
                .port(randomServerPort)

                .when()
                .get("/api/ecmr-import")

                .then()
                .statusCode(HttpStatus.OK.value())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .extract().response();

        List<EcmrImportModel> list = response.as(new TypeRef<>() {
        });
        assertFalse(list.isEmpty());
    }

    private void mockCertDnsValidation(String seal) throws ExecutionException, InterruptedException, TextParseException {
        JWSCompactSerializationParser jwsParser =
                new JWSCompactSerializationParser(new InMemoryDocument(seal.getBytes()));
        JWS jws = jwsParser.parse();
        String fingerprintB64FromSeal = jws.getX509CertSha256ThumbprintHeaderValue();
        byte[] fingerprintBytesFromSeal = Base64.getUrlDecoder().decode(fingerprintB64FromSeal.toLowerCase());
        when(sealDnsFingerprintVerificationService.getTrustedSha256Fingerprints(any(URI.class))).thenReturn(List.of(fingerprintBytesFromSeal));
    }
}
