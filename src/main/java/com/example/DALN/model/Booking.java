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
    private String bookingCode; // Ví dụ: BK-9821

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

    private LocalDate bookingDate;    // Ngày đặt sân
    private String timeSlot;          // 18:00 - 20:00
    private Double totalPrice;        // 360.000đ
    private Double depositAmount;     // 108.000đ (30% cọc sàn giữ)
    private Double remainingAmount;   // 252.000đ (trả tại sân)

    private String paymentStatus;     // PENDING_DEPOSIT, PAID_DEPOSIT, COMPLETED
    private LocalDateTime createdAt;
}