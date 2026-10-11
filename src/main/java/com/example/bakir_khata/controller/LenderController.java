package com.example.bakir_khata.controller;


import com.example.bakir_khata.dto.LenderDTO;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.service.LenderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/lenders")
@RequiredArgsConstructor
public class LenderController {

    private final LenderService lenderService;

    @GetMapping
    public String list(@AuthenticationPrincipal User user,
                       @RequestParam(value = "q", required = false) String q,
                       Model model) {
        model.addAttribute("lenders", lenderService.searchLenders(user, q));
        model.addAttribute("query", q);
        return "lenders/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("lenderDTO")) {
            model.addAttribute("lenderDTO", new LenderDTO());
        }
        return "lenders/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, @AuthenticationPrincipal User user, Model model) {
        var lender = lenderService.getLenderById(id, user);
        LenderDTO dto = new LenderDTO(
                lender.getId(),
                lender.getName(),
                lender.getPhone(),
                lender.getEmail(),
                lender.getAddress(),
                lender.getRelationship(),
                lender.getNotes(),
                null,
                lender.getProfileImagePath()
        );
        model.addAttribute("lenderDTO", dto);
        return "lenders/form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("lenderDTO") LenderDTO dto,
                       BindingResult bindingResult,
                       @AuthenticationPrincipal User user,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "lenders/form";
        }
        lenderService.saveLender(dto, user);
        redirectAttributes.addFlashAttribute("successMessage",
                dto.id() == null ? "Lender added successfully." : "Lender updated successfully.");
        return "redirect:/lenders";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @AuthenticationPrincipal User user,
                         RedirectAttributes redirectAttributes) {
        lenderService.deleteLender(id, user);
        redirectAttributes.addFlashAttribute("successMessage", "Lender deleted.");
        return "redirect:/lenders";
    }
}