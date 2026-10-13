package com.example.DALN.controller;

import com.example.DALN.model.Booking;
import com.example.DALN.model.User;
import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Controller
public class UserController {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, 
                          BookingRepository bookingRepository, 
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
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

    // 2. TRANG LỊCH SỬ ĐẶT SÂN (KẾT NỐI DATABASE THẬT)
    @GetMapping("/lich-su-dat-san")
    public String bookingHistory(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam(name = "code", required = false) String newBookingCode,
                                 Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        User currentUser = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (currentUser != null) {
            // Truy vấn toàn bộ đơn đặt sân của khách này theo thứ tự mới nhất xếp trước
            List<Booking> bookings = bookingRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
            model.addAttribute("bookings", bookings);
            model.addAttribute("user", currentUser);
        }

        model.addAttribute("newBookingCode", newBookingCode);
        return "my-bookings";
    }

    // 3. KHÁCH HÀNG HỦY ĐƠN ĐẶT SÂN
    @PostMapping("/bookings/{id}/cancel")
    public String cancelBooking(@PathVariable Long id,
                                @AuthenticationPrincipal UserDetails userDetails,
                                RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không tìm thấy đơn đặt sân!");
            return "redirect:/lich-su-dat-san";
        }

        // Kiểm tra quyền: Chỉ chính chủ mới được hủy
        if (booking.getUser() != null && !booking.getUser().getUsername().equals(userDetails.getUsername())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Bạn không có quyền thao tác trên đơn này!");
            return "redirect:/lich-su-dat-san";
        }

        if ("COMPLETED".equalsIgnoreCase(booking.getPaymentStatus())) {
            redirectAttributes.addFlashAttribute("errorMessage", "Trận đấu đã kết thúc, không thể hủy đơn!");
            return "redirect:/lich-su-dat-san";
        }

        try {
            // Lấy giờ bắt đầu trận đấu (VD: "08:00 - 09:00" -> "08:00")
            String startTimeStr = booking.getTimeSlot().split("-")[0].trim();
            LocalTime startTime = LocalTime.parse(startTimeStr);
            LocalDateTime matchDateTime = LocalDateTime.of(booking.getBookingDate(), startTime);

            // Quy định: Hủy trước 2 tiếng được hoàn cọc
            if (LocalDateTime.now().plusHours(2).isBefore(matchDateTime)) {
                booking.setPaymentStatus("CANCELLED_REFUND");
                bookingRepository.save(booking);
                redirectAttributes.addFlashAttribute("successMessage", 
                    "Hủy sân thành công! Bạn hủy trước 2 giờ nên đủ điều kiện hoàn 100% tiền cọc (sàn sẽ giải ngân trong 24h).");
            } else {
                booking.setPaymentStatus("CANCELLED_NO_REFUND");
                bookingRepository.save(booking);
                redirectAttributes.addFlashAttribute("errorMessage", 
                    "Đã hủy đơn. Do hủy trong vòng 2 giờ trước trận, tiền cọc 30% được giữ lại để bồi thường cho Chủ sân.");
            }
        } catch (Exception e) {
            booking.setPaymentStatus("CANCELLED");
            bookingRepository.save(booking);
            redirectAttributes.addFlashAttribute("successMessage", "Hủy đơn thành công!");
        }

        return "redirect:/lich-su-dat-san";
    }

    // 4. TRANG ĐỔI MẬT KHẨU
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