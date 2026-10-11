package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller @RequestMapping("/moderation") @RequiredArgsConstructor
public class ModerationController {
    private final CommunityService community;
    @GetMapping public String index(Model model) { model.addAttribute("reports",community.openReports());return "moderation/index"; }
    @PostMapping("/reports/{id}") public String handle(@AuthenticationPrincipal User user,@PathVariable Long id,
                                               @RequestParam boolean hide) {
        community.moderate(user,id,hide);return "redirect:/moderation";
    }
}
