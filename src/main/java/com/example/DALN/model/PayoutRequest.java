package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payout_requests")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String transactionCode; // Mã giao dịch (VD: #PAY-8821)
    private Double amount;          // Số tiền yêu cầu rút
    private String bankInfo;        // Thông tin tài khoản nhận (VD: Vietcombank - 1029384756)
    private String status;          // Trạng thái: PENDING (Chờ duyệt), COMPLETED (Đã chuyển khoản)
    private LocalDateTime requestedAt; // Thời gian yêu cầu

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
}