package com.example.bakir_khata.service;

import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.model.Transaction;
import com.example.bakir_khata.model.enums.TransactionMethod;
import com.example.bakir_khata.model.enums.MobileProvider;
import com.example.bakir_khata.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.Locale;

/**
 * Test gateway ONLY: all URLs are hard-coded to SSLCOMMERZ sandbox endpoints.
 * A successful sandbox payment does not move money to the lender. Never use
 * this service as evidence of a real peer-to-peer transfer.
 */
@Service
public class SslCommerzSandboxGateway {
    private static final String START_URL = "https://sandbox-gw.sslcommerz.com/gwprocess/v4/api.php";
    private static final String VERIFY_URL = "https://sandbox.sslcommerz.com/validator/api/validationserverAPI.php";
    private final String mode;
    private final String storeId;
    private final String storePassword;
    private final String publicBaseUrl;
    private final RestClient client;

    public SslCommerzSandboxGateway(
            @Value("${app.payments.mode:demo}") String mode,
            @Value("${app.payments.sslcommerz.store-id:}") String storeId,
            @Value("${app.payments.sslcommerz.store-password:}") String storePassword,
            @Value("${app.payments.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.mode = mode;
        this.storeId = storeId;
        this.storePassword = storePassword;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(12000);
        this.client = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return "sandbox".equalsIgnoreCase(mode)
                && !storeId.isBlank() && !storePassword.isBlank();
    }

    /** The sandbox demo routes only supported channels to hosted checkout. */
    public boolean supportsHostedCheckout(TransactionMethod method, MobileProvider provider) {
        return isEnabled() && (method == TransactionMethod.BANK
                || (method == TransactionMethod.MOBILE_BANKING && provider == MobileProvider.BKASH));
    }

    /** Hosted sandbox checkout, with no live production URL or fallback. */
    @SuppressWarnings("unchecked")
    public String createHostedCheckout(Transaction tx, User borrower) {
        requireConfigured();
        if (tx.getTransactionReferenceId() == null || tx.getTransactionReferenceId().isBlank()) {
            throw new BusinessRuleException("Cannot initiate sandbox checkout without a transaction reference.");
        }
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("store_id", storeId);
        data.add("store_passwd", storePassword);
        data.add("total_amount", tx.getTotalPayable().toPlainString());
        data.add("currency", "BDT");
        data.add("tran_id", tx.getTransactionReferenceId());
        data.add("success_url", publicBaseUrl + "/payments/sslcommerz/result");
        data.add("fail_url", publicBaseUrl + "/payments/sslcommerz/result");
        data.add("cancel_url", publicBaseUrl + "/payments/sslcommerz/result");
        data.add("ipn_url", publicBaseUrl + "/payments/sslcommerz/ipn");
        // Do not disclose the borrower's real phone or email in a test transaction.
        data.add("cus_name", "Demo Borrower");
        data.add("cus_email", "demo-user-" + borrower.getId() + "@example.org");
        data.add("cus_add1", "University demo project");
        data.add("cus_city", "Dhaka");
        data.add("cus_postcode", "1000");
        data.add("cus_country", "Bangladesh");
        data.add("cus_phone", "01700000000");
        data.add("shipping_method", "NO");
        data.add("product_name", "Bakir Khata Demo Repayment");
        data.add("product_category", "Education Demo");
        data.add("product_profile", "general");
        data.add("multi_card_name", tx.getMethod() == TransactionMethod.BANK ? "internetbank" : "bkash");
        final Map<String, Object> response;
        try {
            response = client.post().uri(START_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(data).retrieve().body(Map.class);
        } catch (RuntimeException ex) {
            throw new BusinessRuleException("Sandbox provider is unavailable. Please try again later.");
        }
        if (response == null || !"SUCCESS".equalsIgnoreCase(String.valueOf(response.get("status")))) {
            throw new BusinessRuleException("Could not start the sandbox payment. Check the sandbox credentials.");
        }
        String gatewayUrl = String.valueOf(response.getOrDefault("GatewayPageURL", ""));
        // Only redirect to the authenticated gateway's sandbox hostname.
        URI uri;
        try {
            uri = URI.create(gatewayUrl);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException("Invalid sandbox checkout URL.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"sandbox.sslcommerz.com".equalsIgnoreCase(uri.getHost())) {
            throw new BusinessRuleException("Unexpected sandbox checkout address.");
        }
        return gatewayUrl;
    }

    /** Independently verify a callback with the provider before changing any loan balance. */
    @SuppressWarnings("unchecked")
    public boolean verifyPayment(String validationId, String expectedReference, BigDecimal expectedAmount,
                                 TransactionMethod expectedMethod) {
        requireConfigured();
        if (validationId == null || validationId.isBlank() || validationId.length() > 80) return false;
        if (expectedReference == null || expectedReference.isBlank()) return false;
        try {
            URI uri = UriComponentsBuilder.fromUriString(VERIFY_URL)
                    .queryParam("val_id", validationId)
                    .queryParam("store_id", storeId)
                    .queryParam("store_passwd", storePassword)
                    .queryParam("format", "json")
                    .build().encode().toUri();
            Map<String, Object> data = client.get().uri(uri).retrieve().body(Map.class);
            if (data == null) return false;
            String status = String.valueOf(data.get("status"));
            if (!"VALID".equalsIgnoreCase(status) && !"VALIDATED".equalsIgnoreCase(status)) return false;
            if (!expectedReference.equals(String.valueOf(data.get("tran_id")))) return false;
            if (!"BDT".equalsIgnoreCase(String.valueOf(data.get("currency")))) return false;
            if (!"0".equals(String.valueOf(data.getOrDefault("risk_level", "1")))) return false;
            String brand = String.valueOf(data.getOrDefault("card_brand", "")).toUpperCase(Locale.ROOT);
            String channel = String.valueOf(data.getOrDefault("card_type", "")).toUpperCase(Locale.ROOT);
            if (expectedMethod == TransactionMethod.BANK && !"IB".equals(brand)) return false;
            if (expectedMethod == TransactionMethod.MOBILE_BANKING
                    && (!brand.contains("MOBILE") || !channel.contains("BKASH"))) return false;
            BigDecimal amount = new BigDecimal(String.valueOf(data.get("amount")));
            return expectedAmount.compareTo(amount) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void requireConfigured() {
        if (!isEnabled()) throw new BusinessRuleException("Sandbox provider is not configured.");
    }

}
