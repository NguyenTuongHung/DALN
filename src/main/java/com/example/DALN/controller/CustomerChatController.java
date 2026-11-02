package com.example.DALN.controller;

import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
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
@RequestMapping("/customer")
public class CustomerChatController {

    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;

    public CustomerChatController(SupportMessageRepository supportMessageRepository,
                                  UserRepository userRepository) {
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
    }

    // 1. Hiển thị trang chat của khách hàng
    @GetMapping("/chat")
    public String customerChatPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";
        return "customer/chat"; // Trỏ đến templates/customer/chat.html
    }

    // 2. API lấy danh sách các chủ sân mà khách hàng này đã từng nhắn tin
    @GetMapping("/api/conversations")
    @ResponseBody
    public List<OwnerConversationDTO> getConversations(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (customer == null) return Collections.emptyList();

        try {
            // Lấy tất cả tin nhắn mà khách hàng này tham gia (gửi đi hoặc nhận về)
            List<SupportMessage> allMessages = supportMessageRepository.findAll().stream()
                    .filter(m -> m != null && m.getSender() != null && 
                                 (m.getSender().getId().equals(customer.getId()) || 
                                  (m.getOwner() != null && m.getOwner().getId().equals(customer.getId()))))
                    .collect(Collectors.toList());

            // Nhóm theo Chủ sân (Owner)
            Map<User, List<SupportMessage>> grouped = allMessages.stream()
                    .filter(m -> m.getOwner() != null && !m.getOwner().getId().equals(customer.getId()))
                    .collect(Collectors.groupingBy(SupportMessage::getOwner));

            List<OwnerConversationDTO> result = new ArrayList<>();
            for (Map.Entry<User, List<SupportMessage>> entry : grouped.entrySet()) {
                User owner = entry.getKey();
                List<SupportMessage> msgs = entry.getValue();
                msgs.sort(Comparator.comparing(SupportMessage::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())));
                
                SupportMessage lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

                result.add(new OwnerConversationDTO(
                        owner.getId(),
                        owner.getBusinessName() != null ? owner.getBusinessName() : owner.getFullName(),
                        lastMsg != null ? lastMsg.getContent() : "Bắt đầu trò chuyện"
                ));
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // 3. API lấy toàn bộ tin nhắn giữa Khách hàng và 1 Chủ sân cụ thể
    @GetMapping("/api/messages")
    @ResponseBody
    public List<ChatMessageDTO> getMessagesWithOwner(@RequestParam Long ownerId,
                                                     @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (customer == null) return Collections.emptyList();

        try {
            List<SupportMessage> allMessages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(ownerId);
            if (allMessages == null) return Collections.emptyList();

            return allMessages.stream()
                    .filter(m -> m != null && m.getSender() != null && 
                                 (m.getSender().getId().equals(customer.getId()) || 
                                  (m.getOwner() != null && m.getOwner().getId().equals(ownerId))))
                    .map(m -> new ChatMessageDTO(
                            m.getId(),
                            m.getContent(),
                            m.getSentAt() != null ? m.getSentAt().toString() : "",
                            m.getSenderRole(),
                            m.getSender().getId()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // 4. API Khách hàng gửi tin nhắn cho Chủ sân
    @PostMapping("/api/send")
    @ResponseBody
    public String customerSendMessage(@RequestParam Long ownerId,
                                      @RequestParam String content,
                                      @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || content == null || content.trim().isEmpty()) {
            return "error";
        }

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        User owner = userRepository.findById(ownerId).orElse(null);
        if (customer == null || owner == null) {
            return "error";
        }

        try {
            SupportMessage message = SupportMessage.builder()
                    .owner(owner)
                    .sender(customer)
                    .senderRole("CUSTOMER") // Đánh dấu tin nhắn do khách gửi
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

    public static class OwnerConversationDTO {
        private Long ownerId;
        private String ownerName;
        private String lastMessage;

        public OwnerConversationDTO(Long ownerId, String ownerName, String lastMessage) {
            this.ownerId = ownerId;
            this.ownerName = ownerName;
            this.lastMessage = lastMessage;
        }

        public Long getOwnerId() { return ownerId; }
        public String getOwnerName() { return ownerName; }
        public String getLastMessage() { return lastMessage; }
    }

    public static class ChatMessageDTO {
        private Long id;
        private String content;
        private String sentAt;
        private String senderRole;
        private Long senderId;

        public ChatMessageDTO(Long id, String content, String sentAt, String senderRole, Long senderId) {
            this.id = id;
            this.content = content;
            this.sentAt = sentAt;
            this.senderRole = senderRole;
            this.senderId = senderId;
        }

        public Long getId() { return id; }
        public String getContent() { return content; }
        public String getSentAt() { return sentAt; }
        public String getSenderRole() { return senderRole; }
        public Long getSenderId() { return senderId; }
    }
}