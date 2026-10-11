package com.example.bakir_khata.service;



import com.example.bakir_khata.dto.PaymentDTO;
import com.example.bakir_khata.model.Payment;
import com.example.bakir_khata.model.User;

import java.util.List;

public interface PaymentService {
    Payment recordPayment(PaymentDTO dto, User user);
    List<Payment> getPaymentsForLoan(Long loanId, User user);
}
