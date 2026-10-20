package org.openlogisticsfoundation.ecmr.cargotrack.sms;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.openlogisticsfoundation.ecmr.domain.services.tan.PhoneMessageProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import lombok.extern.slf4j.Slf4j;

/**
 * Sends guest TAN messages through SMSO (https://app.smso.ro) instead of upstream's logging DummyMessageProvider.
 * Active only with {@code cargotrack.sms.enabled=true}; otherwise the upstream dummy stays in place.
 * <p>
 * API: {@code POST /api/v1/send}, header {@code X-Authorization: <api key>}, form fields {@code sender}, {@code to}, {@code body};
 * answers {@code {"status": 200, "responseToken": "...", "transaction_cost": 3.5}}.
 * <p>
 * Upstream builds an English text ("Your tan code is 123456. Enter the code or click the link: https://...").
 * With {@code cargotrack.sms.template} set, TAN and link are taken from that text and put into the template
 * ({@code {tan}}, {@code {link}}); if upstream changes its wording, the original text is sent unchanged.
 */
@Slf4j
@Primary
@Service
@ConditionalOnProperty(prefix = "cargotrack.sms", name = "enabled", havingValue = "true")
public class SmsoSmsProvider implements PhoneMessageProvider {

    static final Pattern UPSTREAM_TAN_MESSAGE = Pattern.compile("Your tan code is (\\d+)\\. Enter the code or click the link: (\\S+)");

    private final RestClient restClient;
    private final String sender;
    private final String template;

    public SmsoSmsProvider(RestClient.Builder restClientBuilder,
            @Value("${cargotrack.sms.url}") String url,
            @Value("${cargotrack.sms.token}") String apiKey,
            @Value("${cargotrack.sms.sender}") String sender,
            @Value("${cargotrack.sms.template:}") String template) {
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(sender)) {
            throw new IllegalStateException("cargotrack.sms.token and cargotrack.sms.sender are required when cargotrack.sms.enabled=true");
        }
        this.restClient = restClientBuilder
                .baseUrl(url)
                .defaultHeader("X-Authorization", apiKey)
                .build();
        this.sender = sender;
        this.template = template;
    }

    @Override
    public void sendMessage(String recipientIdentifier, String message) throws MessageProviderException {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("sender", sender);
        form.add("to", recipientIdentifier);
        form.add("body", render(message));

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() { });
        } catch (RestClientException e) {
            throw new MessageProviderException(e);
        }

        Object status = response == null ? null : response.get("status");
        if (!(status instanceof Number number) || number.intValue() != 200) {
            throw new MessageProviderException("SMSO rejected the TAN SMS to " + mask(recipientIdentifier) + ": " + response);
        }
        log.info("TAN SMS sent to {} (SMSO token {}, cost {} eurocent)", mask(recipientIdentifier),
                response.get("responseToken"), response.get("transaction_cost"));
    }

    String render(String upstreamMessage) {
        if (!StringUtils.hasText(template)) {
            return upstreamMessage;
        }
        Matcher m = UPSTREAM_TAN_MESSAGE.matcher(upstreamMessage);
        if (!m.find()) {
            log.warn("Unknown upstream TAN message format - sending it unchanged (check cargotrack.sms.template)");
            return upstreamMessage;
        }
        return template.replace("{tan}", m.group(1)).replace("{link}", m.group(2));
    }

    private static String mask(String phone) {
        return phone == null || phone.length() < 4 ? "***" : "***" + phone.substring(phone.length() - 3);
    }
}
