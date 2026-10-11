package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class StaffApiController {
    private final CommunityService community;
    @GetMapping("/api/coordinator/reports") public List<Map<String,Object>> reports() {
        return community.openReports().stream().map(r -> Map.<String,Object>of(
            "reportId",r.getId(),"broadcastId",r.getBroadcast().getId(),
            "reason",r.getReason(),"status",r.getStatus().name())).toList();
    }
    public record ModerationRequest(boolean hide) {}
    @PostMapping("/api/coordinator/reports/{id}") public Map<String,String> moderate(@AuthenticationPrincipal User staff,
                                                            @PathVariable Long id,@RequestBody ModerationRequest req) {
        community.moderate(staff,id,req.hide());
        return Map.of("status","DONE");
    }
    public record PremiumGrant(Long userId,int days) {}
    @PostMapping("/api/admin/plans/grant") public Map<String,String> grant(@AuthenticationPrincipal User admin,
                                                            @RequestBody PremiumGrant req) {
        community.grantPremium(admin,req.userId(),req.days());return Map.of("status","PREMIUM_GRANTED");
    }
}
