package com.ktayl.core.submission.infrastructure.underwriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.ktayl.core.submission.domain.UnderwritingSubmissionRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * L1/L2 — the SUB-02 prod auth: the adapter attaches an {@code Authorization: Bearer} header IFF the token
 * provider yields a token, and sends NO such header when it does not (dev). The underwriting endpoint is
 * stubbed with a {@link MockRestServiceServer} bound to the adapter's own {@code RestClient.Builder}.
 */
class UnderwritingHttpAdapterAuthTest {

    private static final String UW_URL = "http://underwriting.test/v1/submissions";

    private static UnderwritingSubmissionRequest request() {
        return new UnderwritingSubmissionRequest(
                "ACME SA", "NL", "marine", 1_300_000_050L, "warehouse", "NL", "3011AA", "MARINE cover", "BRK-1");
    }

    /** A token provider that always returns the given token (null → empty, i.e. the dev path). */
    private static UnderwritingTokenProvider stubProvider(String token) {
        return new UnderwritingTokenProvider("", "", "", "underwriting:write", RestClient.builder()) {
            @Override
            public Optional<String> token() {
                return Optional.ofNullable(token);
            }
        };
    }

    @Test
    void attaches_the_bearer_when_a_token_is_present_prod() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://underwriting.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnderwritingHttpAdapter adapter = new UnderwritingHttpAdapter(builder, stubProvider("TOK-XYZ"));

        server.expect(once(), requestTo(UW_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer TOK-XYZ"))
                .andRespond(withSuccess("{\"id\":\"11111111-2222-3333-4444-555555555555\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.createSubmission(request())).isEqualTo("11111111-2222-3333-4444-555555555555");
        server.verify();
    }

    @Test
    void omits_the_authorization_header_when_no_token_dev() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://underwriting.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnderwritingHttpAdapter adapter = new UnderwritingHttpAdapter(builder, stubProvider(null));

        server.expect(once(), requestTo(UW_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"id\":\"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(adapter.createSubmission(request())).isEqualTo("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        server.verify();
    }
}
