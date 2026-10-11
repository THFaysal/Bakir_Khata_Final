package com.example.bakir_khata.service;

import com.example.bakir_khata.model.Transaction;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class SimulatedPaymentGateway {
    public GatewayResult process(Transaction transaction) {
        String provider = transaction.getPaymentProvider() == null ? "SIMULATED" : transaction.getPaymentProvider();
        String reference = "SIM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        return new GatewayResult(true, provider, reference, "Simulated payment approved");
    }

    public record GatewayResult(boolean success, String provider, String reference, String message) {}
}
