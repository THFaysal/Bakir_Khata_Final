package com.example.bakir_khata.controller;

import com.example.bakir_khata.dto.RegistrationDTO;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.model.enums.AccountStatus;
import com.example.bakir_khata.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/management/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','COORDINATOR')")
public class ManagementController {
    private final UserService userService;

    @GetMapping
    public String users(Model model) {
        model.addAttribute("users", userService.listUsers());
        if (!model.containsAttribute("registrationDTO")) model.addAttribute("registrationDTO", new RegistrationDTO());
        return "management/users";
    }

    @PostMapping("/create")
    public String create(@Valid @ModelAttribute("registrationDTO") RegistrationDTO dto, BindingResult result,
                         Model model, RedirectAttributes redirectAttributes) {
        if (dto.email() != null && userService.emailExists(dto.email())) result.rejectValue("email", "duplicate", "Email already exists.");
        if (dto.phone() != null && !result.hasFieldErrors("phone") && userService.phoneExists(dto.phone())) result.rejectValue("phone", "duplicate", "Phone already exists.");
        if (!Objects.equals(dto.password(), dto.confirmPassword())) result.rejectValue("confirmPassword", "mismatch", "Passwords do not match.");
        if (result.hasErrors()) {
            model.addAttribute("users", userService.listUsers());
            return "management/users";
        }
        userService.register(dto);
        redirectAttributes.addFlashAttribute("successMessage", "User account created.");
        return "redirect:/management/users";
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable Long id, @RequestParam AccountStatus status,
                         @AuthenticationPrincipal User actor, RedirectAttributes redirectAttributes) {
        userService.setAccountStatus(id, status, actor);
        redirectAttributes.addFlashAttribute("successMessage", "User account status updated to " + status + ".");
        return "redirect:/management/users";
    }
}
