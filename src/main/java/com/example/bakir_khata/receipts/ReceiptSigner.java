package com.example.bakir_khata.receipts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Signs limited-data public receipt verification links. A reference alone is not
 * an authorization token and can never be used to download the private PDF.
 * The configured signing key must stay stable across redeployments.
 */
@Component
public class ReceiptSigner {
    private final String secret;
    private final String baseUrl;

    public ReceiptSigner(@Value("${app.receipts.signing-secret:}") String secret,
                         @Value("${app.receipts.public-base-url:http://localhost:8080}") String baseUrl) {
        this.secret = secret == null ? "" : secret;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return secret.getBytes(StandardCharsets.UTF_8).length >= 32;
    }

    public String sign(ReceiptKind kind, Long id) {
        if (!isConfigured() || kind == null || id == null || id < 1) return null;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Domain-separated so this signature cannot be substituted as a JWT MAC.
            byte[] digest = mac.doFinal(("BK-RECEIPT-V1|" + kind.name() + "|" + id)
                    .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Receipt signing unavailable", ex);
        }
    }

    public boolean matches(ReceiptKind kind, Long id, String supplied) {
        if (supplied == null || !supplied.matches("[0-9a-fA-F]{64}")) return false;
        String expected = sign(kind, id);
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                supplied.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    public String verifyUrl(ReceiptKind kind, Long id) {
        String signature = sign(kind, id);
        if (signature == null || baseUrl.isBlank()) return null;
        return baseUrl + "/receipts/verify?kind=" + kind.name().toLowerCase(Locale.ROOT)
                + "&id=" + id + "&signature=" + signature;
    }
}
