package com.example.DALN.controller;

import com.example.DALN.model.User;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/owner")
public class OwnerController {

    private final UserRepository userRepository;

    public OwnerController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Helper nạp thông tin chủ sân
    private void populateOwnerInfo(UserDetails userDetails, Model model) {
        if (userDetails != null) {
            User owner = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
            model.addAttribute("owner", owner);
        }
    }

    // 1. DASHBOARD: Lịch sân & Đón khách
    @GetMapping({"", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        populateOwnerInfo(userDetails, model);
        model.addAttribute("activeMenu", "dashboard");
        model.addAttribute("todayRevenue", 2450000.0);
        model.addAttribute("netRevenue", 2254000.0);
        model.addAttribute("todayBookingsCount", 7);
        return "owner/dashboard";
    }

    // 2. CÀI ĐẶT GIÁ & GIỜ VÀNG
    @GetMapping("/pricing")
    public String pricingPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        populateOwnerInfo(userDetails, model);
        model.addAttribute("activeMenu", "pricing");
        return "owner/pricing";
    }

    @PostMapping("/pricing/save")
    public String savePricing(@RequestParam(name = "normalPrice") Double normalPrice,
                              @RequestParam(name = "peakPrice") Double peakPrice,
                              @RequestParam(name = "weekendPrice") Double weekendPrice) {
        // Lưu bảng giá vào DB (nếu có model cấu hình)
        return "redirect:/owner/pricing?saved=true";
    }

    // 3. QUYẾT TOÁN & VÍ TIỀN
    @GetMapping("/payout")
    public String payoutPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        populateOwnerInfo(userDetails, model);
        model.addAttribute("activeMenu", "payout");
        model.addAttribute("walletBalance", 41400000.0); // Số dư khả dụng chờ rút
        model.addAttribute("pendingHold", 3250000.0);    // Tiền cọc các trận chưa hoàn thành
        model.addAttribute("totalWithdrawn", 168000000.0);// Đã quyết toán thành công các tháng trước
        return "owner/payout";
    }

    @PostMapping("/payout/request")
    public String requestPayout() {
        // Tạo yêu cầu Admin sàn chuyển khoản quyết toán
        return "redirect:/owner/payout?requested=true";
    }

    // 4. HỖ TRỢ TỪ SÀN ADMIN
    @GetMapping("/support")
    public String supportPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        populateOwnerInfo(userDetails, model);
        model.addAttribute("activeMenu", "support");
        return "owner/support";
    }

    // Xử lý Check-in đón khách
    @PostMapping("/bookings/{id}/check-in")
    public String checkInCustomer(@PathVariable Long id) {
        return "redirect:/owner/dashboard?checkin=success";
    }
}