
package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SupportMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Chủ sân của cuộc trò chuyện
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    // Khách hàng thuộc cuộc trò chuyện
    // Nullable để không ảnh hưởng đến ADMIN_SUPPORT cũ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private User customer;

    // Người thực sự gửi tin nhắn
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    // CUSTOMER hoặc OWNER
    @Column(nullable = false)
    private String senderRole;

    // CUSTOMER_CHAT hoặc ADMIN_SUPPORT
    @Column(name = "chat_type", nullable = false)
    @Builder.Default
    private String chatType = "ADMIN_SUPPORT";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private LocalDateTime sentAt;
}
