/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.net.URI;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.jose4j.lang.JoseException;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.api.model.SealMetadata;
import org.openlogisticsfoundation.ecmr.api.model.SealedDocument;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.InvalidSealException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.ValidationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrRole;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.persistence.entities.CarrierInformationEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrMemberEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.GoodsReceivedEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.ItemEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.SealMetadataEntity;
import org.openlogisticsfoundation.ecmr.persistence.entities.TakingOverTheGoodsEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.SealRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xbill.DNS.TextParseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import ecmr.seal.sign.dss.SignerService;
import ecmr.seal.verify.dss.VerifierService;
import ecmr.seal.verify.rest.ESeal;
import ecmr.seal.verify.rest.SealVerifyResult;
import ecmr.seal.verify.rest.VerifyRequest;
import ecmr.seal.verify.rest.VerifyResponse;
import eu.europa.esig.dss.jades.JWSCompactSerializationParser;
import eu.europa.esig.dss.jades.validation.JWS;
import eu.europa.esig.dss.model.InMemoryDocument;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class SealService {

    private final EcmrService ecmrService;
    private final EcmrStatusService ecmrStatusService;
    private final AuthorisationService authorisationService;
    private final EcmrPersistenceMapper ecmrPersistenceMapper;
    private final SignerService signerService;
    private final SealRepository sealRepository;
    private final SealMetadataService sealMetadataService;
    private final VerifierService verifierService;
    private final SealDnsFingerprintVerificationService sealDnsFingerprintVerificationService;

    @Value("${app.origin.url}")
    private String originUrl;

    static final String CLAIM_NAME = "sealedDocument";

    @Transactional
    public void sealEcmr(UUID ecmrId, InternalOrExternalUser internalOrExternalUser)
            throws EcmrNotFoundException, ValidationException, NoPermissionException {

        Optional<SealMetadataEntity> currentSealMetadata = sealMetadataService.getCurrentSealMetadataEntity(ecmrId, internalOrExternalUser);

        //When currentSealMetadata is present, load the corresponding sealentity and return the actual seal string
        Optional<String> currentSeal = currentSealMetadata.map(sealMetadata -> sealMetadataService.getSealByMetadataId(sealMetadata.getId()));

        // Get the TransportRole of the seal that will be created
        TransportRole nextSealRole = TransportRole.SENDER;
        if (currentSealMetadata.isPresent() && currentSealMetadata.get().getRole() == TransportRole.CONSIGNEE) {
            throw new ValidationException("Ecmr " + ecmrId + " has already a consignee seal");
        } else if (currentSealMetadata.isPresent() && currentSealMetadata.get().getRole() == TransportRole.CARRIER) {
            nextSealRole = TransportRole.CONSIGNEE;
        } else if (currentSealMetadata.isPresent() && currentSealMetadata.get().getRole() == TransportRole.SENDER) {
            nextSealRole = TransportRole.CARRIER;
        }

        EcmrEntity ecmrEntity = ecmrService.getEcmrEntity(ecmrId);
        this.validateEcmrForSealing(nextSealRole, ecmrEntity, internalOrExternalUser, ecmrId);

        this.createSeal(ecmrEntity, currentSeal.orElse(null), internalOrExternalUser, nextSealRole);

        this.ecmrStatusService.setEcmrStatus(ecmrEntity, internalOrExternalUser);
    }

    private void createSeal(EcmrEntity ecmrEntity, @Nullable String precedingSeal, InternalOrExternalUser user, TransportRole role) {
        EcmrModel ecmr = ecmrPersistenceMapper.toModel(ecmrEntity);
        SealMetadata sealMetadata = createSealMetadata(user, role, Instant.now(), originUrl);
        SealedDocument sealedDocument = new SealedDocument();
        sealedDocument.setEcmr(ecmr);
        sealedDocument.setSealMetadata(sealMetadata);

        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_NAME, sealedDocument);
        String seal = signerService.sign(claims, precedingSeal);

        SealMetadataEntity sealMetadataEntity = sealMetadataService.save(sealMetadata, ecmrEntity.getEcmrId());
        this.saveSeal(seal, sealMetadataEntity);
    }

    void saveSeal(String seal, SealMetadataEntity sealMetadataEntity) {
        SealEntity sealEntity = new SealEntity(seal, sealMetadataEntity);
        sealRepository.save(sealEntity);
    }

    public boolean verify(List<ESeal> seals) {
        List<X509Certificate> trustedCertificates = new ArrayList<>();
        for (ESeal seal : seals) {
            try {
                X509Certificate validSeal = this.getValidSeal(seal);
                trustedCertificates.add(validSeal);
            } catch (InvalidSealException e) {
                log.warn("Seal invalid: {}", e.getMessage());
                return false;
            }
        }
        DnsTrustSourceService trustSourceService = new DnsTrustSourceService(trustedCertificates);
        VerifyRequest request = new VerifyRequest(seals, null, true, true, true, true);
        VerifyResponse result = verifierService.verify(request, trustSourceService);
        log.debug("VerifyResponse: {}", result.toString());
        return result.getResult() == SealVerifyResult.VALID;
    }

    private X509Certificate getValidSeal(ESeal seal) throws InvalidSealException {
        try {
            JWS jws = this.parseJWT(seal);
            //Deserialize Seal
            SealedDocument sealedDocument = this.deserializePayloadClaimJson(jws, SealedDocument.class);

            //Load Fingerprints From DNS
            List<byte[]> trustedSha256Fingerprints = sealDnsFingerprintVerificationService.getTrustedSha256Fingerprints(
                    URI.create(sealedDocument.getSealMetadata().getOriginUrl()));
            if (trustedSha256Fingerprints.isEmpty()) {
                throw new InvalidSealException("No trusted fingerprint found in DNS");
            }

            //Get fingerprint from seal
            String fingerprintB64FromSeal = jws.getX509CertSha256ThumbprintHeaderValue();
            if (fingerprintB64FromSeal == null) {
                throw new InvalidSealException("JWS does not contain SHA256-Fingerprint");
            }

            //Check if fingerprints from dns and seal are equal
            byte[] fingerprintBytesFromSeal = Base64.getUrlDecoder().decode(fingerprintB64FromSeal.toLowerCase());
            boolean trusted = trustedSha256Fingerprints.stream()
                    .anyMatch(trustedFingerprint -> MessageDigest.isEqual(trustedFingerprint, fingerprintBytesFromSeal));

            if (!trusted) {
                throw new InvalidSealException("Fingerprints not valid");
            }
            return jws.getLeafCertificateHeaderValue();
        } catch (JsonProcessingException | ExecutionException | InterruptedException | TextParseException | JoseException | IllegalArgumentException e) {
            throw new InvalidSealException(e.getMessage());
        }
    }

    public <T> T deserializePayloadClaimJson(JWS jws, Class<T> clazz) throws JsonProcessingException {
        return deserializePayload(jws, clazz);
    }

    public <T> T deserializePayloadClaimJson(ESeal eseal, Class<T> clazz) throws JsonProcessingException {
        JWS jws = this.parseJWT(eseal);
        return deserializePayload(jws, clazz);
    }

    private JWS parseJWT(ESeal seal) {
        JWSCompactSerializationParser jwsParser = new JWSCompactSerializationParser(new InMemoryDocument(seal.getJadesJwt().getBytes()));
        return jwsParser.parse();
    }

    private static <T> T deserializePayload(JWS jws, Class<T> clazz) throws JsonProcessingException {
        String payload = jws.getUnverifiedPayload();
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Map<String, Object> claims = objectMapper.readValue(payload, new TypeReference<HashMap<String, Object>>() {
        });
        Object ecmrModelObject = claims.get(CLAIM_NAME);
        return objectMapper.convertValue(ecmrModelObject, clazz);
    }

    private SealMetadata createSealMetadata(InternalOrExternalUser user, TransportRole transportRole, Instant timestamp, String originUrl) {
        SealMetadata sealMetadata = new SealMetadata();
        sealMetadata.setRole(transportRole);
        sealMetadata.setTimestamp(timestamp);
        sealMetadata.setSealer(user.getFullName());
        sealMetadata.setSealerCompany(user.getCompanyName());
        sealMetadata.setOriginUrl(originUrl);
        return sealMetadata;
    }

    private void validateEcmrForSealing(TransportRole transportRole, EcmrEntity ecmr,
            InternalOrExternalUser internalOrExternalUser,
            UUID ecmrId) throws ValidationException, NoPermissionException {
        if (transportRole == TransportRole.SENDER) {
            if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Sender)) {
                throw new NoPermissionException("Sender sealing but no Sender Role");
            }
            this.validateEcmrStatus(EcmrStatus.NEW, ecmr);
            this.validateFieldsSender(ecmr);
        } else if (transportRole == TransportRole.CARRIER) {
            if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Carrier)) {
                throw new NoPermissionException("Carrier sealing but no Carrier Role");
            }
            this.validateEcmrStatus(EcmrStatus.LOADING, ecmr);
        } else if (transportRole == TransportRole.CONSIGNEE) {
            if (authorisationService.doesNotHaveRole(internalOrExternalUser, ecmrId, EcmrRole.Consignee)) {
                throw new NoPermissionException("Consignee sealing but no Consignee Role");
            }
            this.validateEcmrStatus(EcmrStatus.IN_TRANSPORT, ecmr);
            this.validateFieldsConsignee(ecmr);
        } else {
            throw new ValidationException("Transport Role " + transportRole + " not implemented");
        }
    }

    private void validateEcmrStatus(EcmrStatus requiredStatus, EcmrEntity ecmr) throws ValidationException {
        if (ecmr.getEcmrStatus() != requiredStatus) {
            throw new ValidationException("Ecmr status needs to be " + requiredStatus.name());
        }
    }

    private void validateFieldsSender(EcmrEntity ecmr) throws ValidationException {
        this.checkEcmrMemberInformation(ecmr.getSenderInformation(), "SenderInformation");
        if (!ecmr.getIsMultiConsigneeShipment()) {
            this.checkEcmrMemberInformation(ecmr.getConsigneeInformation(), "ConsigneeInformation");
        }
        TakingOverTheGoodsEntity takingOverTheGoods = ecmr.getTakingOverTheGoods();
        if (takingOverTheGoods == null) {
            throw new ValidationException("Taking Over The Goods information is missing");
        }
        if (StringUtils.isBlank(takingOverTheGoods.getTakingOverTheGoodsPlace())
                || takingOverTheGoods.getLogisticsTimeOfArrivalDateTime() == null
                || takingOverTheGoods.getLogisticsTimeOfDepartureDateTime() == null) {
            throw new ValidationException("Taking Over The Goods information is missing");
        }
        CarrierInformationEntity carrierInformation = ecmr.getCarrierInformation();
        this.checkEcmrMemberInformation(carrierInformation, "CarrierInformation");
        if (StringUtils.isBlank(carrierInformation.getCarrierLicensePlate())) {
            throw new ValidationException("Carrier License Plate is missing");
        }
        if (StringUtils.isBlank(ecmr.getCustomEstablishedIn())
                || ecmr.getCustomEstablishedDate() == null) {
            throw new ValidationException("Custom Established Information is missing");
        }

        for (ItemEntity item : ecmr.getItemList()) {
            this.checkItem(item);
        }
    }

    private void validateFieldsConsignee(EcmrEntity ecmr) throws ValidationException {
        GoodsReceivedEntity goodsReceived = ecmr.getGoodsReceived();
        if (goodsReceived == null || StringUtils.isBlank(goodsReceived.getConfirmedLogisticsLocationName())) {
            throw new ValidationException("Goods Received is missing");
        }
    }

    private void checkEcmrMemberInformation(EcmrMemberEntity memberEntity, String name) throws ValidationException {
        if (memberEntity == null) {
            throw new ValidationException(name + " is missing");
        }
        if (StringUtils.isBlank(memberEntity.getCompanyName())
                || StringUtils.isBlank(memberEntity.getStreet())
                || StringUtils.isBlank(memberEntity.getPostcode())
                || StringUtils.isBlank(memberEntity.getCity())
                || StringUtils.isBlank(memberEntity.getCountryCode())
        ) {
            throw new ValidationException("Field in " + name + " is missing");
        }
    }

    private void checkItem(ItemEntity item) throws ValidationException {
        if (item == null) {
            throw new ValidationException("Item is missing");
        }
        if (StringUtils.isBlank(item.getLogisticsShippingMarksMarking())
                || item.getLogisticsPackageItemQuantity() == null
                || StringUtils.isBlank(item.getLogisticsPackageType())
                || StringUtils.isBlank(item.getTransportCargoIdentification())
                || item.getSupplyChainConsignmentItemGrossWeight() == null
                || item.getSupplyChainConsignmentItemGrossVolume() == 0) {
            throw new ValidationException("Field in Item is missing");
        }
    }
}
