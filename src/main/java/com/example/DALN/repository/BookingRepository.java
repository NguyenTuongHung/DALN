package com.example.DALN.repository;

import com.example.DALN.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<Booking> findAllByOrderByCreatedAtDesc();
    Optional<Booking> findByBookingCode(String bookingCode);

    boolean existsBySubCourtIdAndBookingDateAndTimeSlot(Long subCourtId, LocalDate bookingDate, String timeSlot);

    @Query("SELECT COALESCE(SUM(b.totalPrice), 0) FROM Booking b WHERE b.paymentStatus = 'PAID_DEPOSIT' OR b.paymentStatus = 'COMPLETED'")
    Double calculateTotalRevenue();
}