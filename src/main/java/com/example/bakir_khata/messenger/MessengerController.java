package com.example.bakir_khata.messenger;

import com.example.bakir_khata.community.CommunityService;
import com.example.bakir_khata.model.User;
import com.example.bakir_khata.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequestMapping("/messenger") @RequiredArgsConstructor
public class MessengerController {
    private final MessengerService messenger;
    private final CommunityService community;
    private final UserRepository users;
    public record ContactView(Long peerId,String messengerId,String displayName) {}
    public record ChatView(String conversationId,String messengerId,String name) {}

    @GetMapping public String inbox(@AuthenticationPrincipal User user,Model model) {
        if (users.findById(user.getId()).filter(User::isCommunityVerified).isEmpty()) return "redirect:/verification";
        model.addAttribute("profile",messenger.identity(user));
        model.addAttribute("contacts",community.connections(user).stream().map(c->{
            Long peerId=c.getBorrower().getId().equals(user.getId())?c.getLender().getId():c.getBorrower().getId();
            var peer=community.findPublicProfile(peerId).orElse(null);
            return new ContactView(peerId,peer==null?"unavailable":peer.getMessengerId(),
                    peer==null?"Member":peer.getDisplayName());
        }).toList());
        model.addAttribute("chats",messenger.inbox(user).stream().map(c->{
            Long peerId=messenger.peerId(user,c);
            var peer=community.findPublicProfile(peerId).orElse(null);
            return new ChatView(c.getId(),peer==null?"unavailable":peer.getMessengerId(),
                    peer==null?"Member":peer.getDisplayName());
        }).toList());
        model.addAttribute("myId",user.getId());
        return "messenger/index";
    }
    @PostMapping("/start") public String start(@AuthenticationPrincipal User user,@RequestParam Long peerId) {
        Conversation conversation=messenger.start(user,peerId);
        return "redirect:/messenger/conversations/"+conversation.getId();
    }
    @PostMapping("/block") public String block(@AuthenticationPrincipal User user,@RequestParam Long peerId) {
        messenger.block(user,peerId);return "redirect:/messenger";
    }
    @PostMapping("/unblock") public String unblock(@AuthenticationPrincipal User user,@RequestParam Long peerId) {
        messenger.unblock(user,peerId);return "redirect:/messenger";
    }
    @GetMapping("/conversations/{id}") public String conversation(@AuthenticationPrincipal User user,
                                               @PathVariable String id,Model model) {
        Conversation conversation=messenger.requireMember(user,id);
        Long peerId=messenger.peerId(user,conversation);
        model.addAttribute("conversation",conversation);
        model.addAttribute("myId",user.getId());
        model.addAttribute("profile",messenger.identity(user));
        model.addAttribute("peer",messenger.person(peerId,user));
        model.addAttribute("messages",messenger.history(user,id));
        model.addAttribute("peerId",peerId);
        model.addAttribute("blocked",messenger.blocked(user.getId(),peerId));
        model.addAttribute("blockedByMe",messenger.blockedByMe(user.getId(),peerId));
        return "messenger/conversation";
    }

}
