package com.example.DALN.controller;

import com.example.DALN.model.User;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // 1. TRANG HỒ SƠ CÁ NHÂN
    @GetMapping("/profile")
    public String viewProfile(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByUsername(userDetails.getUsername())
                .orElse(new User());

        model.addAttribute("user", currentUser);
        return "profile";
    }

    @PostMapping("/profile/update")
    public String updateProfile(@AuthenticationPrincipal UserDetails userDetails,
                                @ModelAttribute("user") User formUser) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        userRepository.findByUsername(userDetails.getUsername()).ifPresent(user -> {
            user.setFullName(formUser.getFullName());
            user.setPhone(formUser.getPhone());
            userRepository.save(user);
        });

        return "redirect:/profile?success=true";
    }

    // 2. TRANG LỊCH SỬ ĐẶT SÂN
    @GetMapping("/lich-su-dat-san")
    public String bookingHistory(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        User currentUser = userRepository.findByUsername(userDetails.getUsername()).orElse(new User());
        model.addAttribute("user", currentUser);
        return "booking-history";
    }

    // 3. TRANG ĐỔI MẬT KHẨU
    @GetMapping("/doi-mat-khau")
    public String changePasswordPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        return "change-password";
    }

    @PostMapping("/doi-mat-khau")
    public String handleChangePassword(@AuthenticationPrincipal UserDetails userDetails,
                                       @RequestParam("oldPassword") String oldPassword,
                                       @RequestParam("newPassword") String newPassword,
                                       @RequestParam("confirmPassword") String confirmPassword,
                                       Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (user == null) {
            return "redirect:/login";
        }

        // Kiểm tra mật khẩu cũ
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            model.addAttribute("error", "Mật khẩu hiện tại không chính xác!");
            return "change-password";
        }

        // Kiểm tra xác nhận mật khẩu mới
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "Mật khẩu xác nhận không khớp!");
            return "change-password";
        }

        if (newPassword.length() < 6) {
            model.addAttribute("error", "Mật khẩu mới phải có ít nhất 6 ký tự!");
            return "change-password";
        }

        // Lưu mật khẩu mới đã mã hóa
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        model.addAttribute("success", "Đổi mật khẩu thành công!");
        return "change-password";
    }
}