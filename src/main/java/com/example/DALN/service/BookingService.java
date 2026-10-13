package com.example.DALN.service;

import com.example.DALN.model.Booking;
import com.example.DALN.model.Court;
import com.example.DALN.model.SubCourt;
import com.example.DALN.model.User;
import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SubCourtRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final SubCourtRepository subCourtRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          CourtRepository courtRepository,
                          SubCourtRepository subCourtRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.courtRepository = courtRepository;
        this.subCourtRepository = subCourtRepository;
        this.userRepository = userRepository;
    }

    // 1. Tạo đơn đặt cọc mới
    @Transactional
    public Booking createBooking(Long courtId, Long subCourtId, String username, 
                                 String customerName, String customerPhone, String customerNote,
                                 LocalDate bookingDate, String timeSlot, Double duration) {
        
        Court court = courtRepository.findById(courtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cụm sân!"));
        SubCourt subCourt = subCourtRepository.findById(subCourtId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sân con!"));

        // Kiểm tra chống trùng lịch
        boolean isTaken = bookingRepository.existsBySubCourtIdAndBookingDateAndTimeSlot(subCourtId, bookingDate, timeSlot);
        if (isTaken) {
            throw new IllegalStateException("Khung giờ này vừa có người đặt trước, vui lòng chọn giờ khác!");
        }

        User user = null;
        if (username != null) {
            user = userRepository.findByUsername(username).orElse(null);
        }

        // Tính tiền: giá sân/giờ * thời lượng
        double unitPrice = court.getPricePerHour() != null ? court.getPricePerHour() : 100000.0;
        double totalPrice = unitPrice * (duration != null ? duration : 1.0);
        double depositAmount = totalPrice * 0.3; // Sàn giữ 30% tiền cọc
        double remainingAmount = totalPrice - depositAmount;

        String bookingCode = "BK-" + (1000 + new Random().nextInt(9000));

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .court(court)
                .subCourt(subCourt)
                .user(user)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerNote(customerNote)
                .bookingDate(bookingDate)
                .timeSlot(timeSlot)
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .remainingAmount(remainingAmount)
                .paymentStatus("PAID_DEPOSIT") // Đã thanh toán cọc qua VietQR
                .createdAt(LocalDateTime.now())
                .build();

        return bookingRepository.save(booking);
    }

    // 2. Người dùng yêu cầu hủy đơn (Chính sách: trước 2 giờ được hoàn cọc)
    @Transactional
    public String cancelBookingByUser(Long bookingId, String username) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn đặt sân!"));

        // Kiểm tra quyền sở hữu đơn
        if (booking.getUser() != null && !booking.getUser().getUsername().equals(username)) {
            throw new SecurityException("Bạn không có quyền thao tác trên đơn này!");
        }

        if ("COMPLETED".equalsIgnoreCase(booking.getPaymentStatus())) {
            throw new IllegalStateException("Trận đấu đã hoàn thành, không thể hủy!");
        }

        // Tách giờ bắt đầu từ timeSlot (VD: "08:00 - 09:00" -> lấy 08:00)
        String startTimeStr = booking.getTimeSlot().split("-")[0].trim();
        LocalTime startTime = LocalTime.parse(startTimeStr);
        LocalDateTime matchDateTime = LocalDateTime.of(booking.getBookingDate(), startTime);

        // Kiểm tra điều kiện trước 2 tiếng
        if (LocalDateTime.now().plusHours(2).isBefore(matchDateTime)) {
            booking.setPaymentStatus("CANCELLED_REFUND"); // Hủy và hoàn cọc
            bookingRepository.save(booking);
            return "Hủy đơn thành công! Bạn đủ điều kiện hoàn 100% tiền cọc (sàn sẽ giải ngân trong 24h).";
        } else {
            booking.setPaymentStatus("CANCELLED_NO_REFUND"); // Hủy muộn, mất cọc
            bookingRepository.save(booking);
            return "Đã hủy đơn. Do hủy trong vòng 2 giờ trước trận đấu, tiền cọc 30% sẽ được bồi thường cho Chủ sân.";
        }
    }
}