package org.openlogisticsfoundation.ecmr.cargotrack.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.openlogisticsfoundation.ecmr.domain.services.tan.MessageProviderException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

class SmsoSmsProviderTest {

    private static final String URL = "https://app.smso.ro/api/v1/send";
    private static final String UPSTREAM_MESSAGE =
            "Your tan code is 123456. Enter the code or click the link: https://ecmr.example.ro/ecmr-tan/abc/Xy12/123456";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private SmsoSmsProvider provider(String template) {
        return new SmsoSmsProvider(builder, URL, "test-key", "609", template);
    }

    @Test
    void sendsFormWithApiKeyHeader() throws Exception {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("sender", "609");
        form.add("to", "+40722334455");
        form.add("body", UPSTREAM_MESSAGE);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Authorization", "test-key"))
                .andExpect(content().formData(form))
                .andRespond(withSuccess("{\"status\":200,\"responseToken\":\"t\",\"transaction_cost\":3.5}", MediaType.APPLICATION_JSON));

        provider("").sendMessage("+40722334455", UPSTREAM_MESSAGE);

        server.verify();
    }

    @Test
    void failsWhenSmsoReportsAnError() {
        server.expect(requestTo(URL))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"status\":401}").contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider("").sendMessage("+40722334455", UPSTREAM_MESSAGE))
                .isInstanceOf(MessageProviderException.class);
    }

    @Test
    void failsWhenBodyStatusIsNot200() {
        server.expect(requestTo(URL))
                .andRespond(withSuccess("{\"status\":400,\"message\":\"Invalid number\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider("").sendMessage("+40722334455", UPSTREAM_MESSAGE))
                .isInstanceOf(MessageProviderException.class)
                .hasMessageContaining("Invalid number");
    }

    @Test
    void rendersTemplateWithTanAndLink() {
        String rendered = provider("Cod eCMR: {tan}. Link: {link}").render(UPSTREAM_MESSAGE);

        assertThat(rendered).isEqualTo("Cod eCMR: 123456. Link: https://ecmr.example.ro/ecmr-tan/abc/Xy12/123456");
    }

    @Test
    void keepsUpstreamTextWhenFormatIsUnknown() {
        assertThat(provider("Cod eCMR: {tan}").render("Something else")).isEqualTo("Something else");
    }

    @Test
    void requiresApiKeyAndSender() {
        assertThatThrownBy(() -> new SmsoSmsProvider(builder, URL, "", "609", ""))
                .isInstanceOf(IllegalStateException.class);
    }
}
