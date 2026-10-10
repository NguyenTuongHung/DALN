package com.example.DALN.controller;

import com.example.DALN.model.User;
import com.example.DALN.repository.UserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;

    public AdminController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // 1. TỔNG QUAN
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        return "admin/dashboard";
    }

    // 2. QUẢN LÝ SÂN BÃI
    @GetMapping("/courts")
    public String courtsManagement(Model model) {
        model.addAttribute("activePage", "courts");
        return "admin/courts";
    }

    // 3. ĐƠN ĐẶT SÂN
    @GetMapping("/bookings")
    public String bookingsManagement(Model model) {
        model.addAttribute("activePage", "bookings");
        return "admin/bookings";
    }

    // 4. KHÁCH HÀNG & CHỦ SÂN
    @GetMapping("/users")
    public String usersManagement(@RequestParam(name = "tab", defaultValue = "owners") String tab, Model model) {
        model.addAttribute("activeTab", tab);
        model.addAttribute("owners", userRepository.findByRole("ROLE_OWNER"));
        model.addAttribute("customers", userRepository.findByRole("ROLE_USER"));
        model.addAttribute("activePage", "users");
        return "admin/users";
    }

    // Duyệt / Khóa tài khoản
    @PostMapping("/users/{id}/toggle-status")
    public String toggleStatus(@PathVariable Long id, @RequestParam("tab") String tab) {
        userRepository.findById(id).ifPresent(u -> {
            u.setStatus("ACTIVE".equalsIgnoreCase(u.getStatus()) ? "LOCKED" : "ACTIVE");
            userRepository.save(u);
        });
        return "redirect:/admin/users?tab=" + tab;
    }

    // Cập nhật hoa hồng chủ sân
    @PostMapping("/owners/{id}/update-commission")
    public String updateCommission(@PathVariable Long id, @RequestParam("commissionRate") Double rate) {
        userRepository.findById(id).ifPresent(owner -> {
            owner.setCommissionRate(rate);
            userRepository.save(owner);
        });
        return "redirect:/admin/users?tab=owners";
    }

    // 5. BÁO CÁO DOANH THU
    @GetMapping("/reports")
    public String reportsManagement(Model model) {
        model.addAttribute("activePage", "reports");
        return "admin/reports";
    }

    // 6. CÀI ĐẶT HỆ THỐNG
    @GetMapping("/settings")
    public String settingsManagement(Model model) {
        model.addAttribute("activePage", "settings");
        return "admin/settings";
    }
}