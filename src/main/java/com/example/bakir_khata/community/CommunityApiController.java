package com.example.bakir_khata.community;

import com.example.bakir_khata.model.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController @RequestMapping("/api/community") @RequiredArgsConstructor
public class CommunityApiController {
    private final CommunityService community;
    public record PublishRequest(@NotNull @DecimalMin("1") @DecimalMax("10000000") BigDecimal amount,
                                 @NotBlank @Size(max=150) String purpose,
                                 @NotBlank @Size(max=1000) String description,
                                 @Min(1) @Max(365) int durationDays) {}
    public record OfferRequest(@NotNull Long broadcastId,@NotNull @DecimalMin("1") BigDecimal amount,
                               @NotBlank @Size(max=600) String note) {}
    public record BroadcastView(Long id,String messengerId,String name,BigDecimal amount,String purpose,
                                String description,int durationDays,String status) {}
    @GetMapping("/feed") public List<BroadcastView> feed(@AuthenticationPrincipal User user) {
        return community.publicFeed(user).stream().map(b -> {
            return new BroadcastView(b.getId(), b.getMessengerId(), b.getName(),
                   b.getAmount(), b.getPurpose(), b.getDescription(),
                   b.getDurationDays(), b.getStatus().name());
        }).toList();
    }
    @PostMapping("/broadcasts") public BroadcastView publish(@AuthenticationPrincipal User user,
                                                      @Valid @RequestBody PublishRequest request) {
        LoanBroadcast b=community.publish(user,request.amount(),request.purpose(),request.description(),request.durationDays());
        CommunityProfile p=community.profile(user);
        return new BroadcastView(b.getId(),p.getMessengerId(),p.getDisplayName(),b.getAmount(),b.getPurpose(),b.getDescription(),b.getDurationDays(),b.getStatus().name());
    }
    @PostMapping("/offers") public java.util.Map<String,Object> offer(@AuthenticationPrincipal User user,
                                                     @Valid @RequestBody OfferRequest req) {
        LendingOffer offer=community.offer(user,req.broadcastId(),req.amount(),req.note());
        return java.util.Map.of("offerId",offer.getId(),"status",offer.getStatus().name());
    }
    @PostMapping("/offers/{id}/accept") public java.util.Map<String,Object> accept(@AuthenticationPrincipal User user,@PathVariable Long id) {
        LoanAgreement ag=community.accept(user,id);
        return java.util.Map.of("agreementId",ag.getId(),"status",ag.getStatus().name());
    }
    @PostMapping("/agreements/{id}/cancel") public java.util.Map<String,String> cancel(@AuthenticationPrincipal User user,@PathVariable Long id) {
        community.cancelPendingAgreement(user,id);
        return java.util.Map.of("status","DECLINED");
    }
    @PostMapping("/agreements/{id}/confirm") public java.util.Map<String,Object> confirm(@AuthenticationPrincipal User user,@PathVariable Long id) {
        LoanAgreement ag=community.confirmDisbursement(user,id);
        return java.util.Map.of("loanId",ag.getLoan().getId(),"status",ag.getStatus().name(),"demo",true);
    }
}
