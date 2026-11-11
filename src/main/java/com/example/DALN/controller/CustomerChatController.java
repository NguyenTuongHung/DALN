package com.example.DALN.controller;

import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
import com.example.DALN.repository.SupportMessageRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
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

    @GetMapping("/chat")
    public String customerChatPage(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return "redirect:/login";
        return "customer/chat";
    }

    // =========================================================================
    // API MỚI: KIỂM TRA TRẠNG THÁI CÓ TIN NHẮN CHƯA ĐỌC HAY KHÔNG (CHO CHẤM ĐỎ MENU)
    // =========================================================================
    @GetMapping("/api/unread-status")
    @ResponseBody
    public Map<String, Object> getUnreadStatus(@AuthenticationPrincipal UserDetails userDetails) {
        Map<String, Object> response = new HashMap<>();
        response.put("hasUnread", false);

        if (userDetails == null) return response;

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (customer == null) return response;

        try {
            // Đếm số tin nhắn chưa đọc do OWNER gửi cho khách hàng này trong luồng CUSTOMER_CHAT
            long unreadCount = supportMessageRepository.countByCustomerIdAndChatTypeAndSenderRoleAndIsReadFalse(
                    customer.getId(), 
                    "CUSTOMER_CHAT", 
                    "OWNER"
            );

            response.put("hasUnread", unreadCount > 0);
            response.put("count", unreadCount);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return response;
    }

    @GetMapping("/api/conversations")
    @ResponseBody
    public List<OwnerConversationDTO> getOwnerConversations(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (customer == null) return Collections.emptyList();

        try {
            List<SupportMessage> allMessages = supportMessageRepository.findAll();
            if (allMessages == null) return Collections.emptyList();

            Map<User, List<SupportMessage>> mapByOwner = new HashMap<>();
            for (SupportMessage m : allMessages) {
                if (m == null) continue;
                // Chấp nhận cả CUSTOMER_CHAT hoặc tin nhắn cũ chưa set chatType
                String type = m.getChatType();
                if (type != null && !"CUSTOMER_CHAT".equalsIgnoreCase(type)) continue;
                if (m.getOwner() == null) continue;

                boolean isSentByCustomer = m.getSender() != null && m.getSender().getId().equals(customer.getId());
                boolean isOwnerReply = "OWNER".equalsIgnoreCase(m.getSenderRole());

                if (isSentByCustomer || isOwnerReply) {
                    mapByOwner.computeIfAbsent(m.getOwner(), k -> new ArrayList<>()).add(m);
                }
            }

            List<OwnerConversationDTO> result = new ArrayList<>();
            for (Map.Entry<User, List<SupportMessage>> entry : mapByOwner.entrySet()) {
                User owner = entry.getKey();
                List<SupportMessage> msgs = entry.getValue();
                if (owner == null) continue;

                msgs.sort(Comparator.comparing(SupportMessage::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())));
                SupportMessage lastMsg = msgs.isEmpty() ? null : msgs.get(msgs.size() - 1);

                result.add(new OwnerConversationDTO(
                        owner.getId(),
                        owner.getFullName() != null ? owner.getFullName() : owner.getUsername(),
                        lastMsg != null ? lastMsg.getContent() : "Đã kết nối"
                ));
            }
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    @GetMapping("/api/messages")
    @ResponseBody
    @Transactional // Cần thiết khi thực hiện câu lệnh update (markMessagesAsReadForCustomer)
    public List<CustomerChatMessageDTO> getMessagesWithParameters(@RequestParam Long ownerId,
                                                                  @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) return Collections.emptyList();

        User customer = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (customer == null) return Collections.emptyList();

        try {
            // TỰ ĐỘNG ĐÁNH DẤU LÀ ĐÃ ĐỌC KHI KHÁCH HÀNG MỞ HỘI THOẠI VỚI CHỦ SÂN NÀY
            supportMessageRepository.markMessagesAsReadForCustomer(customer.getId(), ownerId, "CUSTOMER_CHAT");

            List<SupportMessage> allMessages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(ownerId);
            if (allMessages == null) return Collections.emptyList();

            return allMessages.stream()
                    .filter(m -> {
                        if (m == null) return false;
                        String type = m.getChatType();
                        if (type != null && !"CUSTOMER_CHAT".equalsIgnoreCase(type)) return false;

                        boolean isFromCustomer = m.getSender() != null && m.getSender().getId().equals(customer.getId());
                        boolean isOwnerReply = "OWNER".equalsIgnoreCase(m.getSenderRole());
                        return isFromCustomer || isOwnerReply;
                    })
                    .map(m -> new CustomerChatMessageDTO(
                            m.getId(),
                            m.getContent(),
                            m.getSentAt() != null ? m.getSentAt().toString() : "",
                            m.getSenderRole(),
                            m.getSender() != null ? m.getSender().getId() : null
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

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
                    .customer(customer) // Đảm bảo gán customer vào để hàm count/mark hoạt động chính xác
                    .sender(customer)
                    .senderRole("CUSTOMER")
                    .chatType("CUSTOMER_CHAT")
                    .content(content.trim())
                    .sentAt(LocalDateTime.now())
                    .isRead(false) // Tin nhắn do customer gửi mặc định là chưa đọc đối với owner (hoặc tùy logic)
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

    public static class CustomerChatMessageDTO {
        private Long id;
        private String content;
        private String sentAt;
        private String senderRole;
        private Long senderId;

        public CustomerChatMessageDTO(Long id, String content, String sentAt, String senderRole, Long senderId) {
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