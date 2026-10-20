package org.openlogisticsfoundation.ecmr.cargotrack.sms;

import java.util.Map;

import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.openlogisticsfoundation.ecmr.domain.services.tan.PhoneMessageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends guest TAN messages through the CargoTrack SMS gateway instead of upstream's logging DummyMessageProvider.
 * Active only with {@code cargotrack.sms.enabled=true}; otherwise the upstream dummy stays in place.
 * <p>
 * The request shape (JSON {@code {"to": ..., "message": ...}} with a bearer token) is a placeholder -
 * adapt it to the actual gateway/vendor API.
 */
@Slf4j
@Primary
@Service
@ConditionalOnProperty(prefix = "cargotrack.sms", name = "enabled", havingValue = "true")
public class CargoTrackSmsProvider implements PhoneMessageProvider {

    private final RestClient restClient;

    public CargoTrackSmsProvider(@Value("${cargotrack.sms.url}") String url,
            @Value("${cargotrack.sms.token}") String token) {
        this.restClient = RestClient.builder()
                .baseUrl(url)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    @Override
    public void sendMessage(String recipientIdentifier, String message) throws MessageProviderException {
        try {
            restClient.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("to", recipientIdentifier, "message", message))
                    .retrieve()
                    .toBodilessEntity();
            log.info("TAN SMS sent to {}", mask(recipientIdentifier));
        } catch (RestClientException e) {
            throw new MessageProviderException(e);
        }
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "***" : "***" + phone.substring(phone.length() - 3);
    }
}
