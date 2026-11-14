package com.ktayl.core.billing.payment;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ktayl.core.shared.config.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * L3 / security — the Stripe M2M webhook contract: a signature-valid {@code payment_intent.succeeded} is
 * accepted (200) and handed to capture; a forged signature is rejected (400) and NEVER reaches capture; and
 * the endpoint is reachable WITHOUT SSO (SecurityConfig permits it — the signature is the only gate). The
 * capture service is mocked (its DB behaviour is the L2 test); JwtDecoder is mocked to skip the OIDC fetch.
 */
@WebMvcTest(StripeWebhookController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "billing.stripe.webhook-secret=whsec_test_secret")
class StripeWebhookControllerTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD = """
            {"id":"evt_test_1","object":"event","type":"payment_intent.succeeded",
             "data":{"object":{"id":"pi_test_1","object":"payment_intent"}}}""";

    @Autowired MockMvc mvc;
    @MockitoBean PaymentCaptureService capture;
    @MockitoBean JwtDecoder jwtDecoder;

    private static String signature(String payload, String secret, long t) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hmac = mac.doFinal((t + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + t + ",v1=" + HexFormat.of().formatHex(hmac);
    }

    @Test
    void accepts_a_signature_valid_event_and_captures_it() throws Exception {
        String sig = signature(PAYLOAD, SECRET, Instant.now().getEpochSecond());

        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", sig)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isOk());

        verify(capture).capturePaymentSucceeded(eq("evt_test_1"), eq("payment_intent.succeeded"), eq("pi_test_1"));
    }

    @Test
    void rejects_a_forged_signature_with_400_and_never_captures() throws Exception {
        String forged = "t=" + Instant.now().getEpochSecond() + ",v1=deadbeef";

        mvc.perform(post("/webhooks/stripe").header("Stripe-Signature", forged)
                        .contentType(MediaType.APPLICATION_JSON).content(PAYLOAD))
                .andExpect(status().isBadRequest());

        verify(capture, never()).capturePaymentSucceeded(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }
}
