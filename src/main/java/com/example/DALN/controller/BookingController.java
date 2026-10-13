package com.example.DALN.controller;

import com.example.DALN.model.Booking;
import com.example.DALN.model.Court;
import com.example.DALN.model.SubCourt;
import com.example.DALN.model.User;
import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SubCourtRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Controller
public class BookingController {

    private final CourtRepository courtRepository;
    private final SubCourtRepository subCourtRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;

    public BookingController(CourtRepository courtRepository, 
                             SubCourtRepository subCourtRepository, 
                             BookingRepository bookingRepository, 
                             UserRepository userRepository) {
        this.courtRepository = courtRepository;
        this.subCourtRepository = subCourtRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    // 1. TÌM KIẾM SÂN (CHỈ LẤY CÁC SÂN ĐÃ ĐƯỢC ADMIN DUYỆT - ACTIVE)
    @GetMapping("/tim-san")
    public String searchCourts(@RequestParam(name = "keyword", required = false) String keyword,
                               @RequestParam(name = "type", required = false, defaultValue = "all") String sportType,
                               Model model) {
        List<Court> allMatched = courtRepository.searchCourts(keyword, sportType);

        // Lọc điều kiện: Chỉ sân đã được Admin phê duyệt (ACTIVE) hoặc sân mẫu sẵn có (null)
        List<Court> approvedCourts = allMatched.stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                .collect(Collectors.toList());

        model.addAttribute("courts", approvedCourts);
        model.addAttribute("totalCourts", approvedCourts.size());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedType", sportType);
        return "search";
    }

    // 2. CHI TIẾT SÂN THẬT (CHẶN NẾU SÂN CHƯA ĐƯỢC DUYỆT)
    @GetMapping("/san-bong/{id}")
    public String courtDetail(@PathVariable Long id, Model model) {
        Court court = courtRepository.findById(id).orElse(null);
        if (court == null) {
            return "redirect:/tim-san";
        }

        // Nếu sân đang PENDING hoặc REJECTED thì không cho khách truy cập trực tiếp bằng URL
        if (court.getStatus() != null && !"ACTIVE".equalsIgnoreCase(court.getStatus())) {
            return "redirect:/tim-san";
        }

        List<SubCourt> subCourts = subCourtRepository.findByCourtId(id);
        model.addAttribute("court", court);
        model.addAttribute("subCourts", subCourts);
        model.addAttribute("today", LocalDate.now().toString());
        return "court-detail";
    }

    // 3. API TRẢ VỀ CÁC KHUNG GIỜ ĐÃ BỊ ĐẶT (ĐỂ BÔI XÁM / DISABLE TRÊN GIAO DIỆN)
    @GetMapping("/api/bookings/booked-slots")
    @ResponseBody
    public List<String> getBookedSlots(@RequestParam Long subCourtId, 
                                       @RequestParam(required = false) String date) {
        LocalDate queryDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<Booking> existingBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getSubCourt() != null && b.getSubCourt().getId().equals(subCourtId))
                .filter(b -> queryDate.equals(b.getBookingDate()))
                .filter(b -> !"CANCELLED_REFUND".equalsIgnoreCase(b.getPaymentStatus()) 
                          && !"CANCELLED_NO_REFUND".equalsIgnoreCase(b.getPaymentStatus())
                          && !"CANCELLED".equalsIgnoreCase(b.getPaymentStatus()))
                .collect(Collectors.toList());

        return existingBookings.stream()
                .map(Booking::getTimeSlot)
                .collect(Collectors.toList());
    }

    // 4. GIAO DIỆN CHỌN SÂN & XÁC NHẬN ĐẶT CỌC (CHECKOUT)
    @GetMapping("/dat-san/checkout")
    public String checkoutPage(@RequestParam(name = "courtId", defaultValue = "1") Long courtId,
                               @RequestParam(name = "subCourtId", required = false) Long subCourtId,
                               @RequestParam(name = "time", required = false) String timeSlot,
                               @AuthenticationPrincipal UserDetails userDetails,
                               Model model) {
        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null || (court.getStatus() != null && !"ACTIVE".equalsIgnoreCase(court.getStatus()))) {
            return "redirect:/tim-san";
        }

        List<SubCourt> subCourts = subCourtRepository.findByCourtId(courtId);
        SubCourt selectedSubCourt = (subCourtId != null) 
                ? subCourtRepository.findById(subCourtId).orElse(subCourts.isEmpty() ? null : subCourts.get(0))
                : (subCourts.isEmpty() ? null : subCourts.get(0));

        model.addAttribute("court", court);
        model.addAttribute("subCourts", subCourts);
        model.addAttribute("selectedSubCourt", selectedSubCourt);
        model.addAttribute("selectedTime", timeSlot != null ? timeSlot : "18:00 - 19:00");
        model.addAttribute("today", LocalDate.now().toString());

        if (userDetails != null) {
            userRepository.findByUsername(userDetails.getUsername()).ifPresent(u -> model.addAttribute("currentUser", u));
        }

        return "checkout";
    }

    // 5. LƯU ĐƠN ĐẶT SÂN VÀO DATABASE (KIỂM TRA CHỐNG TRÙNG LỊCH)
    @PostMapping("/dat-san/confirm")
    public String confirmBooking(@RequestParam Long courtId,
                                 @RequestParam(required = false, defaultValue = "1") Long subCourtId,
                                 @RequestParam(required = false) String bookingDate,
                                 @RequestParam(required = false, defaultValue = "08:00 - 09:00") String timeSlot,
                                 @RequestParam(required = false, defaultValue = "100000") Double totalPrice,
                                 @RequestParam(required = false, defaultValue = "30000") Double depositAmount,
                                 @RequestParam String customerName,
                                 @RequestParam String customerPhone,
                                 @RequestParam(required = false) String customerNote,
                                 @AuthenticationPrincipal UserDetails userDetails,
                                 RedirectAttributes redirectAttributes) {

        Court court = courtRepository.findById(courtId).orElse(null);
        SubCourt subCourt = subCourtRepository.findById(subCourtId).orElse(null);
        User user = (userDetails != null) ? userRepository.findByUsername(userDetails.getUsername()).orElse(null) : null;

        LocalDate date = (bookingDate != null && !bookingDate.isBlank()) ? LocalDate.parse(bookingDate) : LocalDate.now();

        // 1. Kiểm tra chống trùng giờ: Khung giờ đã có người giữ cọc hoặc khóa chưa?
        boolean isAlreadyBooked = bookingRepository.findAll().stream()
                .filter(b -> b.getSubCourt() != null && b.getSubCourt().getId().equals(subCourtId))
                .filter(b -> date.equals(b.getBookingDate()))
                .filter(b -> timeSlot.equalsIgnoreCase(b.getTimeSlot()))
                .anyMatch(b -> !"CANCELLED_REFUND".equalsIgnoreCase(b.getPaymentStatus()) 
                            && !"CANCELLED_NO_REFUND".equalsIgnoreCase(b.getPaymentStatus())
                            && !"CANCELLED".equalsIgnoreCase(b.getPaymentStatus()));

        if (isAlreadyBooked) {
            redirectAttributes.addFlashAttribute("errorMessage", "Khung giờ này vừa có người đặt trước! Vui lòng chọn giờ khác.");
            return "redirect:/san-bong/" + courtId;
        }

        // 2. Tạo mã đơn ngẫu nhiên và lưu
        String bookingCode = "BK-" + (1000 + new Random().nextInt(9000));

        Booking booking = Booking.builder()
                .bookingCode(bookingCode)
                .court(court)
                .subCourt(subCourt)
                .user(user)
                .customerName(customerName)
                .customerPhone(customerPhone)
                .customerNote(customerNote)
                .bookingDate(date)
                .timeSlot(timeSlot)
                .totalPrice(totalPrice)
                .depositAmount(depositAmount)
                .remainingAmount(totalPrice - depositAmount)
                .paymentStatus("PAID_DEPOSIT")
                .createdAt(LocalDateTime.now())
                .build();

        bookingRepository.save(booking);

        return "redirect:/lich-su-dat-san?code=" + bookingCode;
    }
}