package com.example.bakir_khata.service;



import com.example.bakir_khata.dto.LenderDTO;
import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.User;

import java.util.List;

public interface LenderService {
    Lender saveLender(LenderDTO dto, User user);
    List<Lender> getAllLenders(User user);
    List<Lender> searchLenders(User user, String term);
    Lender getLenderById(Long id, User user);
    void deleteLender(Long id, User user);
}
