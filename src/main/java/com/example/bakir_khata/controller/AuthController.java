package com.example.bakir_khata.controller;


import com.example.bakir_khata.dto.RegistrationDTO;
import com.example.bakir_khata.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Objects;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    // Separate branded entry point for admins - same processing URL (/login) as the
    // regular form above, same AuthenticationManager/UserDetailsService, just a
    // differently-branded template.
    @GetMapping("/admin/login")
    public String adminLoginPage() {
        return "auth/admin-login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("registrationDTO")) {
            model.addAttribute("registrationDTO", new RegistrationDTO());
        }
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registrationDTO") RegistrationDTO dto,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes) {

        if (dto.email() != null && userService.emailExists(dto.email())) {
            bindingResult.rejectValue("email", "duplicate", "An account with this email already exists.");
        }
        if (dto.phone() != null && !bindingResult.hasFieldErrors("phone") && userService.phoneExists(dto.phone())) {
            bindingResult.rejectValue("phone", "duplicate", "An account with this phone number already exists.");
        }
        if (!Objects.equals(dto.password(), dto.confirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        }

        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        userService.register(dto);
        redirectAttributes.addFlashAttribute("successMessage", "Account created successfully. Please log in.");
        return "redirect:/login";
    }
}