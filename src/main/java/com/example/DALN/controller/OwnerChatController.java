package com.example.DALN.controller;

import com.example.DALN.model.Court;
import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SupportMessageRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/owner")
public class OwnerChatController {

    private final CourtRepository courtRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;

    public OwnerChatController(CourtRepository courtRepository,
                               SupportMessageRepository supportMessageRepository,
                               UserRepository userRepository) {
        this.courtRepository = courtRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/chat")
    public String ownerChatPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";

        User owner = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (owner == null) return "redirect:/login";

        List<Court> ownerCourts = courtRepository.findAll().stream()
                .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                .collect(Collectors.toList());
        model.addAttribute("ownerCourts", ownerCourts);

        return "owner/chat";
    }

    @GetMapping("/api/conversations")
    @ResponseBody
    public List<CustomerConversationDTO> getConversations(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User owner = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (owner == null) return Collections.emptyList();

        try {
            List<SupportMessage> allMessages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(owner.getId());
            if (allMessages == null) return Collections.emptyList();

            Map<User, List<SupportMessage>> grouped = allMessages.stream()
                    .filter(m -> m != null && m.getSender() != null && !"OWNER".equalsIgnoreCase(m.getSenderRole()))
                    .collect(Collectors.groupingBy(SupportMessage::getSender));

            List<CustomerConversationDTO> result = new ArrayList<>();
            for (Map.Entry<User, List<SupportMessage>> entry : grouped.entrySet()) {
                User customer = entry.getKey();
                List<SupportMessage> msgs = entry.getValue();
                if (customer == null) continue;

                SupportMessage lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

                result.add(new CustomerConversationDTO(
                        customer.getId(),
                        customer.getFullName() != null ? customer.getFullName() : customer.getUsername(),
                        lastMsg != null ? lastMsg.getContent() : "Đã gửi tin nhắn"
                ));
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // API lấy tin nhắn trả về dạng DTO an toàn tuyệt đối, tránh lỗi 500 do Jackson serialization
    @GetMapping("/api/messages")
    @ResponseBody
    public List<ChatMessageDTO> getMessagesWithCustomer(@RequestParam Long customerId,
                                                        @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User owner = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (owner == null) return Collections.emptyList();

        try {
            List<SupportMessage> allMessages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(owner.getId());
            if (allMessages == null) return Collections.emptyList();

            return allMessages.stream()
                    .filter(m -> {
                        if (m == null || m.getSender() == null) return false;
                        boolean isFromCustomer = m.getSender().getId().equals(customerId) && !"OWNER".equalsIgnoreCase(m.getSenderRole());
                        boolean isOwnerReply = "OWNER".equalsIgnoreCase(m.getSenderRole()) && m.getOwner() != null && m.getOwner().getId().equals(owner.getId());
                        return isFromCustomer || isOwnerReply;
                    })
                    .map(m -> new ChatMessageDTO(
                            m.getId(),
                            m.getContent(),
                            m.getSentAt() != null ? m.getSentAt().toString() : "",
                            m.getSenderRole(),
                            m.getSender() != null ? m.getSender().getId() : null,
                            m.getSender() != null ? m.getSender().getUsername() : ""
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @PostMapping("/api/send")
    @ResponseBody
    public String ownerSendMessage(@RequestParam Long customerId,
                                   @RequestParam String content,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || content == null || content.trim().isEmpty()) {
            return "error";
        }

        User owner = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        User customer = userRepository.findById(customerId).orElse(null);
        if (owner == null || customer == null) {
            return "error";
        }

        try {
            SupportMessage message = SupportMessage.builder()
                    .owner(owner)
                    .sender(customer)
                    .senderRole("OWNER")
                    .content(content.trim())
                    .sentAt(LocalDateTime.now())
                    .build();

            supportMessageRepository.save(message);
            return "success";
        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    public static class CustomerConversationDTO {
        private Long customerId;
        private String customerName;
        private String lastMessage;

        public CustomerConversationDTO(Long customerId, String customerName, String lastMessage) {
            this.customerId = customerId;
            this.customerName = customerName;
            this.lastMessage = lastMessage;
        }

        public Long getCustomerId() { return customerId; }
        public String getCustomerName() { return customerName; }
        public String getLastMessage() { return lastMessage; }
    }

    public static class ChatMessageDTO {
        private Long id;
        private String content;
        private String sentAt;
        private String senderRole;
        private Long senderId;
        private String senderName;

        public ChatMessageDTO(Long id, String content, String sentAt, String senderRole, Long senderId, String senderName) {
            this.id = id;
            this.content = content;
            this.sentAt = sentAt;
            this.senderRole = senderRole;
            this.senderId = senderId;
            this.senderName = senderName;
        }

        public Long getId() { return id; }
        public String getContent() { return content; }
        public String getSentAt() { return sentAt; }
        public String getSenderRole() { return senderRole; }
        public Long getSenderId() { return senderId; }
        public String getSenderName() { return senderName; }
    }
}