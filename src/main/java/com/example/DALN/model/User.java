package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username; // Tên đăng nhập hoặc email

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String phone;

    @Column(nullable = false)
    private String role; // "ROLE_USER", "ROLE_OWNER", "ROLE_ADMIN"

    // --- Thông tin mở rộng dành riêng cho Chủ sân (ROLE_OWNER) ---
    private String businessName;       // Tên cơ sở / Hộ kinh doanh cụm sân
    private String bankName;           // Tên ngân hàng nhận quyết toán (VD: Vietcombank)
    private String bankAccountNumber;  // Số tài khoản ngân hàng
    private String bankAccountName;    // Tên chủ tài khoản ngân hàng

    @Builder.Default
    private Double commissionRate = 8.0; // Tỷ lệ hoa hồng sàn thu (%)

    @Builder.Default
    private String status = "ACTIVE"; // "ACTIVE", "LOCKED", "PENDING_APPROVAL"
}