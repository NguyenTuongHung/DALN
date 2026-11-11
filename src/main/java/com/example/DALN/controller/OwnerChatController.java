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
import org.springframework.transaction.annotation.Transactional;
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

    public OwnerChatController(
            CourtRepository courtRepository,
            SupportMessageRepository supportMessageRepository,
            UserRepository userRepository) {

        this.courtRepository = courtRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
    }

    // =====================================================
    // 1. TRANG CHAT CHỦ SÂN
    // =====================================================

    @GetMapping("/chat")
    @Transactional(readOnly = true)
    public String ownerChatPage(
            Model model,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        User owner = userRepository
                .findByUsername(userDetails.getUsername())
                .orElse(null);

        if (owner == null
                || !"ROLE_OWNER".equals(owner.getRole())) {
            return "redirect:/login";
        }

        List<Court> ownerCourts = courtRepository.findAll()
                .stream()
                .filter(c -> c.getOwner() != null
                        && Objects.equals(
                                c.getOwner().getId(),
                                owner.getId()))
                .collect(Collectors.toList());

        model.addAttribute("ownerCourts", ownerCourts);

        return "owner/chat";
    }

    // =====================================================
    // 2. DANH SÁCH KHÁCH HÀNG ĐÃ TRÒ CHUYỆN
    // =====================================================

    @GetMapping("/api/conversations")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<CustomerConversationDTO> getConversations(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return Collections.emptyList();
        }

        User owner = userRepository
                .findByUsername(userDetails.getUsername())
                .orElse(null);

        if (owner == null
                || !"ROLE_OWNER".equals(owner.getRole())) {
            return Collections.emptyList();
        }

        try {
            // Lấy toàn bộ tin nhắn để quét và lọc linh hoạt, tránh phụ thuộc strict query
            List<SupportMessage> allMessages = supportMessageRepository.findAll();

            if (allMessages == null || allMessages.isEmpty()) {
                return Collections.emptyList();
            }

            // Lọc các tin nhắn thuộc về chủ sân này và đúng luồng CUSTOMER_CHAT
            Map<Long, List<SupportMessage>> grouped = new HashMap<>();
            for (SupportMessage m : allMessages) {
                if (m == null || m.getOwner() == null) continue;
                if (!Objects.equals(m.getOwner().getId(), owner.getId())) continue;

                // Kiểm tra loại chat (cho phép CUSTOMER_CHAT hoặc null nếu là dữ liệu cũ)
                String type = m.getChatType();
                if (type != null && !"CUSTOMER_CHAT".equalsIgnoreCase(type)) continue;

                User customer = m.getCustomer() != null ? m.getCustomer() : m.getSender();
                if (customer == null) continue;

                grouped.computeIfAbsent(customer.getId(), k -> new ArrayList<>()).add(m);
            }

            List<CustomerConversationDTO> result = new ArrayList<>();

            for (Map.Entry<Long, List<SupportMessage>> entry : grouped.entrySet()) {
                Long customerId = entry.getKey();
                if (customerId == null) continue;

                User customer = userRepository.findById(customerId).orElse(null);
                if (customer == null) continue;

                List<SupportMessage> msgs = entry.getValue();
                SupportMessage lastMsg = getLastMessage(msgs);

                String customerName =
                        customer.getFullName() != null
                                && !customer.getFullName().trim().isEmpty()
                                ? customer.getFullName()
                                : customer.getUsername();

                String lastMessage =
                        lastMsg != null
                                && lastMsg.getContent() != null
                                ? lastMsg.getContent()
                                : "Đã kết nối";

                LocalDateTime lastMessageTime =
                        lastMsg != null
                                ? lastMsg.getSentAt()
                                : null;

                result.add(new CustomerConversationDTO(
                        customer.getId(),
                        customerName,
                        lastMessage,
                        lastMessageTime
                ));
            }

            // Sắp xếp cuộc trò chuyện mới nhất lên đầu
            result.sort((a, b) -> {
                LocalDateTime timeA = a.getLastMessageTime();
                LocalDateTime timeB = b.getLastMessageTime();

                if (timeA == null && timeB == null) return 0;
                if (timeA == null) return 1;
                if (timeB == null) return -1;

                return timeB.compareTo(timeA);
            });

            return result;

        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    // =====================================================
    // 3. LẤY TIN NHẮN VỚI MỘT KHÁCH HÀNG CỤ THỂ
    // =====================================================

    @GetMapping("/api/messages")
    @ResponseBody
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessagesWithCustomer(
            @RequestParam Long customerId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null || customerId == null) {
            return Collections.emptyList();
        }

        User owner = userRepository
                .findByUsername(userDetails.getUsername())
                .orElse(null);

        if (owner == null
                || !"ROLE_OWNER".equals(owner.getRole())) {
            return Collections.emptyList();
        }

        try {
            List<SupportMessage> allMessages = supportMessageRepository.findAll();
            if (allMessages == null) {
                return Collections.emptyList();
            }

            return allMessages.stream()
                    .filter(Objects::nonNull)
                    .filter(m -> m.getOwner() != null && Objects.equals(m.getOwner().getId(), owner.getId()))
                    .filter(m -> {
                        String type = m.getChatType();
                        return type == null || "CUSTOMER_CHAT".equalsIgnoreCase(type);
                    })
                    .filter(m -> {
                        User cust = m.getCustomer() != null ? m.getCustomer() : m.getSender();
                        return cust != null && Objects.equals(cust.getId(), customerId);
                    })
                    .sorted(Comparator.comparing(SupportMessage::getSentAt, Comparator.nullsLast(Comparator.naturalOrder())))
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

    // =====================================================
    // 4. CHỦ SÂN GỬI TIN NHẮN CHO KHÁCH HÀNG
    // =====================================================

    @PostMapping("/api/send")
    @ResponseBody
    @Transactional
    public String ownerSendMessage(
            @RequestParam Long customerId,
            @RequestParam String content,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null
                || customerId == null
                || content == null
                || content.trim().isEmpty()) {
            return "error";
        }

        String messageContent = content.trim();
        if (messageContent.length() > 5000) {
            return "error";
        }

        User owner = userRepository
                .findByUsername(userDetails.getUsername())
                .orElse(null);

        User customer = userRepository
                .findById(customerId)
                .orElse(null);

        if (owner == null
                || !"ROLE_OWNER".equals(owner.getRole())
                || customer == null) {
            return "error";
        }

        try {
            SupportMessage message = SupportMessage.builder()
                    .owner(owner)
                    .customer(customer)
                    .sender(owner)
                    .senderRole("OWNER")
                    .chatType("CUSTOMER_CHAT")
                    .content(messageContent)
                    .sentAt(LocalDateTime.now())
                    .build();

            supportMessageRepository.save(message);
            return "success";

        } catch (Exception e) {
            e.printStackTrace();
            return "error";
        }
    }

    // =====================================================
    // 5. LẤY TIN NHẮN CUỐI CÙNG
    // =====================================================

    private SupportMessage getLastMessage(
            List<SupportMessage> messages) {

        if (messages == null || messages.isEmpty()) {
            return null;
        }

        return messages.stream()
                .filter(Objects::nonNull)
                .max(Comparator.comparing(
                        SupportMessage::getSentAt,
                        Comparator.nullsFirst(
                                Comparator.naturalOrder()
                        )
                ))
                .orElse(null);
    }

    // =====================================================
    // 6. DTO DANH SÁCH KHÁCH HÀNG
    // =====================================================

    public static class CustomerConversationDTO {

        private Long customerId;
        private String customerName;
        private String lastMessage;
        private LocalDateTime lastMessageTime;

        public CustomerConversationDTO(
                Long customerId,
                String customerName,
                String lastMessage,
                LocalDateTime lastMessageTime) {

            this.customerId = customerId;
            this.customerName = customerName;
            this.lastMessage = lastMessage;
            this.lastMessageTime = lastMessageTime;
        }

        public Long getCustomerId() {
            return customerId;
        }

        public String getCustomerName() {
            return customerName;
        }

        public String getLastMessage() {
            return lastMessage;
        }

        public LocalDateTime getLastMessageTime() {
            return lastMessageTime;
        }
    }

    // =====================================================
    // 7. DTO TIN NHẮN
    // =====================================================

    public static class ChatMessageDTO {

        private Long id;
        private String content;
        private String sentAt;
        private String senderRole;
        private Long senderId;
        private String senderName;

        public ChatMessageDTO(
                Long id,
                String content,
                String sentAt,
                String senderRole,
                Long senderId,
                String senderName) {

            this.id = id;
            this.content = content;
            this.sentAt = sentAt;
            this.senderRole = senderRole;
            this.senderId = senderId;
            this.senderName = senderName;
        }

        public Long getId() {
            return id;
        }

        public String getContent() {
            return content;
        }

        public String getSentAt() {
            return sentAt;
        }

        public String getSenderRole() {
            return senderRole;
        }

        public Long getSenderId() {
            return senderId;
        }

        public String getSenderName() {
            return senderName;
        }
    }
}