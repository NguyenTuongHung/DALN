
package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingCode;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "court_id")
    private Court court;

    @ManyToOne
    @JoinColumn(name = "sub_court_id")
    private SubCourt subCourt;

    private String customerName;

    private String customerPhone;

    private String customerNote;

    private LocalDate bookingDate;

    private String timeSlot;

    private Double totalPrice;

    private Double depositAmount;

    private Double remainingAmount;

    // Trạng thái thanh toán:
    // PENDING_DEPOSIT, PAID_DEPOSIT, COMPLETED
    private String paymentStatus;

    // Trạng thái đón khách: NOT_CHECKED_IN, CHECKED_IN
    @Builder.Default
    private String checkInStatus = "NOT_CHECKED_IN";

    private LocalDateTime createdAt;
}
