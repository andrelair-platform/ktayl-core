package com.ktayl.core.submission.infrastructure.underwriting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * L1/L2 — the OAuth2 client-credentials token provider: it is OFF until configured, caches a minted token,
 * and refreshes only when the token nears expiry. The token endpoint is stubbed with a
 * {@link MockRestServiceServer} bound to the provider's own {@code RestClient.Builder} — no network.
 */
class UnderwritingTokenProviderTest {

    private static final String TOKEN_URI = "https://auth.example/application/o/token/";

    /** Builds a provider whose token endpoint is backed by the returned mock server. */
    private static Harness configured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnderwritingTokenProvider provider = new UnderwritingTokenProvider(
                TOKEN_URI, "ktayl-core-sub", "s3cret", "underwriting:write", builder);
        return new Harness(provider, server);
    }

    private record Harness(UnderwritingTokenProvider provider, MockRestServiceServer server) {}

    @Test
    void yields_no_token_when_client_id_is_blank() {
        UnderwritingTokenProvider dev = new UnderwritingTokenProvider(
                "", "", "", "underwriting:write", RestClient.builder());
        assertThat(dev.token()).isEmpty();
    }

    @Test
    void mints_a_form_encoded_client_credentials_token_scoped_underwriting_write() {
        Harness h = configured();
        h.server().expect(once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials",
                        "client_id", "ktayl-core-sub",
                        "client_secret", "s3cret",
                        "scope", "underwriting:write")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"TOK-1\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
                        MediaType.APPLICATION_JSON));

        assertThat(h.provider().token()).contains("TOK-1");
        h.server().verify();
    }

    @Test
    void caches_the_token_and_mints_only_once_while_it_is_fresh() {
        Harness h = configured();
        // expires_in 3600s → well outside the 60s refresh skew → ONE mint serves many calls.
        h.server().expect(once(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"TOK-FRESH\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        for (int i = 0; i < 5; i++) {
            assertThat(h.provider().token()).contains("TOK-FRESH");
        }
        h.server().verify(); // once() → fails if the provider minted more than one
    }

    @Test
    void refreshes_when_the_cached_token_is_within_the_expiry_skew() {
        Harness h = configured();
        // expires_in 10s < the 60s refresh skew → every call sees a "near-expiry" token → re-mints each time.
        h.server().expect(times(3), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"TOK-SHORT\",\"expires_in\":10}",
                        MediaType.APPLICATION_JSON));

        for (int i = 0; i < 3; i++) {
            assertThat(h.provider().token()).contains("TOK-SHORT");
        }
        h.server().verify(); // times(3) → proves it refreshed on every near-expiry call
    }

    @Test
    void defaults_the_scope_when_none_is_given() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UnderwritingTokenProvider provider = new UnderwritingTokenProvider(
                TOKEN_URI, "cid", "sec", "   ", builder);
        server.expect(once(), requestTo(TOKEN_URI))
                .andExpect(content().formDataContains(java.util.Map.of("scope", "underwriting:write")))
                .andRespond(withSuccess("{\"access_token\":\"T\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        assertThat(provider.token()).isPresent();
        server.verify();
    }

    @Test
    void optional_empty_is_a_dev_signal_not_an_exception() {
        Optional<String> dev = new UnderwritingTokenProvider("", "", "", "", RestClient.builder()).token();
        assertThat(dev).isEmpty();
    }
}
