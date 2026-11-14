package com.srliq.app.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Vérification du secret du webhook en temps constant : on compare les empreintes SHA-256 avec
 * {@link MessageDigest#isEqual}, ce qui ne révèle ni le contenu ni la longueur du secret.
 */
@Component
public class WebhookTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookTokenVerifier.class);

    private final byte[] expectedDigest;

    public WebhookTokenVerifier(WebhookProperties properties) {
        String token = properties.getToken();
        if (token == null || token.isBlank()) {
            log.warn("srliq.webhook.token absent : le webhook TradingView est désactivé (503)");
            expectedDigest = null;
        } else if (token.strip().length() < WebhookProperties.MIN_TOKEN_LENGTH) {
            log.warn("srliq.webhook.token trop court (< {} caractères) : le webhook TradingView est désactivé (503)",
                    WebhookProperties.MIN_TOKEN_LENGTH);
            expectedDigest = null;
        } else {
            expectedDigest = sha256(token.strip());
        }
    }

    public boolean isConfigured() {
        return expectedDigest != null;
    }

    public boolean matches(String candidate) {
        if (expectedDigest == null || candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedDigest, sha256(candidate));
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
