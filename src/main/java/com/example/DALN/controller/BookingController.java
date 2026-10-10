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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

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

    // 1. TÌM KIẾM SÂN (DATABASE THẬT)
    @GetMapping("/tim-san")
    public String searchCourts(@RequestParam(name = "keyword", required = false) String keyword,
                               @RequestParam(name = "type", required = false, defaultValue = "all") String sportType,
                               Model model) {
        List<Court> courts = courtRepository.searchCourts(keyword, sportType);

        model.addAttribute("courts", courts);
        model.addAttribute("totalCourts", courtRepository.count());
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedType", sportType);
        return "search";
    }

    // 2. CHI TIẾT SÂN THẬT
    @GetMapping("/san-bong/{id}")
    public String courtDetail(@PathVariable Long id, Model model) {
        Court court = courtRepository.findById(id).orElse(null);
        if (court == null) {
            return "redirect:/tim-san";
        }

        List<SubCourt> subCourts = subCourtRepository.findByCourtId(id);
        model.addAttribute("court", court);
        model.addAttribute("subCourts", subCourts);
        model.addAttribute("today", LocalDate.now().toString());
        return "court-detail";
    }

    // 3. GIAO DIỆN CHỌN SÂN & LỊCH (CHECKOUT)
    @GetMapping("/dat-san/checkout")
    public String checkoutPage(@RequestParam(name = "courtId", defaultValue = "1") Long courtId,
                               @RequestParam(name = "subCourtId", required = false) Long subCourtId,
                               @AuthenticationPrincipal UserDetails userDetails,
                               Model model) {
        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null) {
            return "redirect:/tim-san";
        }

        List<SubCourt> subCourts = subCourtRepository.findByCourtId(courtId);
        SubCourt selectedSubCourt = (subCourtId != null) 
                ? subCourtRepository.findById(subCourtId).orElse(subCourts.isEmpty() ? null : subCourts.get(0))
                : (subCourts.isEmpty() ? null : subCourts.get(0));

        model.addAttribute("court", court);
        model.addAttribute("subCourts", subCourts);
        model.addAttribute("selectedSubCourt", selectedSubCourt);
        model.addAttribute("today", LocalDate.now().toString());

        if (userDetails != null) {
            userRepository.findByUsername(userDetails.getUsername()).ifPresent(u -> model.addAttribute("currentUser", u));
        }

        return "checkout";
    }

    // 4. LƯU ĐƠN ĐẶT SÂN VÀO DATABASE
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
                                 @AuthenticationPrincipal UserDetails userDetails) {

        Court court = courtRepository.findById(courtId).orElse(null);
        SubCourt subCourt = subCourtRepository.findById(subCourtId).orElse(null);
        User user = (userDetails != null) ? userRepository.findByUsername(userDetails.getUsername()).orElse(null) : null;

        String bookingCode = "BK-" + (1000 + new Random().nextInt(9000));
        LocalDate date = (bookingDate != null && !bookingDate.isBlank()) ? LocalDate.parse(bookingDate) : LocalDate.now();

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