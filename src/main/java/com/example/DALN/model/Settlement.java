
package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_settlement_owner_month",
        columnNames = {"owner_id", "settlement_month"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "settlement_month", nullable = false, length = 7)
    private String settlementMonth;

    private Double grossRevenue;
    private Double commissionAmount;
    private Double payoutAmount;

    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;

    @Column(nullable = false)
    private String status;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = "TRANSFER_CREATED";
        }
    }
}
