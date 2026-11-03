package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String username; // Tên đăng nhập hoặc email

    @Column(nullable = false)
    private String password; // Mật khẩu đã được mã hóa BCrypt

    @Column(length = 150)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, length = 50)
    private String role; // "ROLE_USER", "ROLE_OWNER", "ROLE_ADMIN"

    // --- Thông tin mở rộng dành riêng cho Chủ sân (ROLE_OWNER) ---
    @Column(length = 200)
    private String businessName;         // Tên cơ sở / Hộ kinh doanh cụm sân

    @Column(length = 100)
    private String bankName;             // Tên ngân hàng nhận quyết toán (VD: Vietcombank)

    @Column(length = 50)
    private String bankAccountNumber;    // Số tài khoản ngân hàng

    @Column(length = 150)
    private String bankAccountName;      // Tên chủ tài khoản ngân hàng (viết hoa không dấu)

    @Builder.Default
    @Column(nullable = false)
    private Double commissionRate = 8.0; // Tỷ lệ hoa hồng sàn thu (%)

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";    // "ACTIVE", "LOCKED", "PENDING_APPROVAL"
}