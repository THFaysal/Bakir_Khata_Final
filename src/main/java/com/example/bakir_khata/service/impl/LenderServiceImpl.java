package com.example.bakir_khata.service.impl;


import com.example.bakir_khata.dto.LenderDTO;
import com.example.bakir_khata.exception.BusinessRuleException;
import com.example.bakir_khata.exception.ResourceNotFoundException;
import com.example.bakir_khata.model.Lender;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.LenderRepository;
import com.example.bakir_khata.repository.LoanRepository;
import com.example.bakir_khata.service.FileStorageService;
import com.example.bakir_khata.service.LenderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LenderServiceImpl implements LenderService {

    private final LenderRepository lenderRepository;
    private final LoanRepository loanRepository;
    private final FileStorageService fileStorageService;

    @Override
    public Lender saveLender(LenderDTO dto, User user) {
        Lender lender = dto.id() == null
                ? new Lender()
                : getLenderById(dto.id(), user);

        lender.setName(dto.name());
        lender.setPhone(dto.phone());
        lender.setEmail(dto.email());
        lender.setAddress(dto.address());
        lender.setRelationship(dto.relationship());
        lender.setNotes(dto.notes());
        lender.setUser(user);

        if (dto.profileImage() != null && !dto.profileImage().isEmpty()) {
            String path = fileStorageService.store(dto.profileImage(), "lenders");
            lender.setProfileImagePath(path);
        } else if (StringUtils.hasText(dto.existingProfileImagePath())) {
            lender.setProfileImagePath(dto.existingProfileImagePath());
        }

        // Community counterparts are linked only after accepting an offer.
        // Preserve a prior explicit link on edit, but never infer one from contact data.

        return lenderRepository.save(lender);
    }

    @Override
    public List<Lender> getAllLenders(User user) {
        return lenderRepository.findByUserOrderByNameAsc(user);
    }

    @Override
    public List<Lender> searchLenders(User user, String term) {
        if (!StringUtils.hasText(term)) {
            return getAllLenders(user);
        }
        return lenderRepository.search(user, term.trim());
    }

    @Override
    public Lender getLenderById(Long id, User user) {
        Lender lender = lenderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lender not found"));
        if (!lender.getUser().equals(user)) {
            throw new ResourceNotFoundException("Lender not found");
        }
        return lender;
    }

    @Override
    public void deleteLender(Long id, User user) {
        Lender lender = getLenderById(id, user);
        if (!loanRepository.findByLender_IdAndUser(id, user).isEmpty()) {
            throw new BusinessRuleException("Cannot delete a lender with existing loans.");
        }
        lenderRepository.delete(lender);
    }
}