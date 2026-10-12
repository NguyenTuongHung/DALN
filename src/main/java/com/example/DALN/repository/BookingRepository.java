
package com.example.DALN.repository;

import com.example.DALN.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Lịch sử đặt sân của khách hàng
    List<Booking> findByUserIdOrderByCreatedAtDesc(Long userId);

    // Tất cả đơn, mới nhất trước
    List<Booking> findAllByOrderByCreatedAtDesc();

    List<Booking> findByBookingDateBetweenAndPaymentStatusIn(
    LocalDate startDate,
    LocalDate endDate,
    Collection<String> paymentStatuses
);
    // Tìm đơn theo mã
    Optional<Booking> findByBookingCode(String bookingCode);

    // Kiểm tra trùng sân con, ngày và khung giờ
    boolean existsBySubCourtIdAndBookingDateAndTimeSlot(
            Long subCourtId,
            LocalDate bookingDate,
            String timeSlot
    );

    // Tổng giá trị đơn đã đặt cọc hoặc hoàn thành
    @Query("""
        SELECT COALESCE(SUM(b.totalPrice), 0)
        FROM Booking b
        WHERE b.paymentStatus = 'PAID_DEPOSIT'
           OR b.paymentStatus = 'COMPLETED'
        """)
    Double calculateTotalRevenue();

    // Đếm đơn theo thời điểm tạo
    long countByCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    // 10 đơn mới nhất
    List<Booking> findTop10ByOrderByCreatedAtDesc();

    // Đếm đơn theo trạng thái thanh toán
    long countByPaymentStatus(String paymentStatus);

    // Đếm đơn theo ngày đặt sân
    long countByBookingDate(LocalDate bookingDate);

    // Tổng tiền đặt cọc theo trạng thái
    @Query("""
        SELECT COALESCE(SUM(b.depositAmount), 0)
        FROM Booking b
        WHERE b.paymentStatus = :status
        """)
    Double calculateDepositsByStatus(
            @Param("status") String status
    );
}
