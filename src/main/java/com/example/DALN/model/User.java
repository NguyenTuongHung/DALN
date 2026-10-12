
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
    private String username;

    @Column(nullable = false)
    private String password;

    private String fullName;
    private String phone;

    @Column(nullable = false)
    private String role;

    // Thông tin dành cho chủ sân
    private String businessName;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;

    @Builder.Default
    private Double commissionRate = 8.0;

    @Builder.Default
    private String status = "ACTIVE";
}