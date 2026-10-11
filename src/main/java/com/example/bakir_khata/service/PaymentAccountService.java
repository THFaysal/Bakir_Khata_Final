package com.example.bakir_khata.service;

import com.example.bakir_khata.model.PaymentAccount;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.MobileProvider;

import java.util.List;

public interface PaymentAccountService {
    List<PaymentAccount> listForUser(User user);
    PaymentAccount addBank(User user, String bankName, String accountHolderName, String accountNumber,
                           String branchName, String routingNumber);
    PaymentAccount updateBank(Long id, User user, String bankName, String accountHolderName, String accountNumber,
                              String branchName, String routingNumber);
    PaymentAccount addMobile(User user, MobileProvider provider, String mobileNumber);
    PaymentAccount updateMobile(Long id, User user, MobileProvider provider, String mobileNumber);
    OtpService.OtpDelivery sendMobileVerification(Long id, User user);
    boolean confirmMobileVerification(Long id, User user, String code);
    void deactivate(Long id, User user);
}
