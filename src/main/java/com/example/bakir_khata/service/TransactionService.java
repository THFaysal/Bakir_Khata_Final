package com.example.bakir_khata.service;

import com.example.bakir_khata.dto.PaymentInitiationDTO;
import com.example.bakir_khata.dto.TransactionDTO;
import com.example.bakir_khata.dto.TransactionSubmitDTO;
import com.example.bakir_khata.model.User;

import java.util.List;

public interface TransactionService {
    TransactionDTO initiatePayment(PaymentInitiationDTO dto, User borrower);
    TransactionDTO submit(TransactionSubmitDTO dto, User borrower);
    TransactionDTO accept(Long transactionId, User lenderUser);
    TransactionDTO reject(Long transactionId, User lenderUser);
    TransactionDTO cancel(Long transactionId, User borrower);
    TransactionDTO getDemoPayment(Long transactionId, User borrower);
    TransactionDTO completeDemoPayment(Long transactionId, User borrower, boolean approved);
    void confirmSandboxCallback(String transactionReferenceId, String validationId);
    void failSandboxInitiation(Long transactionId, User borrower);

    List<TransactionDTO> getPendingForCurrentUser(User lenderUser);
    List<TransactionDTO> getSubmittedByCurrentUser(User borrower);
    List<TransactionDTO> getAllForReview();
    void setReviewFlag(Long transactionId, boolean flagged, String reviewNote, User reviewer);
}
