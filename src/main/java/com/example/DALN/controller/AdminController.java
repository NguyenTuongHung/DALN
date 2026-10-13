package com.example.DALN.controller;

import com.example.DALN.model.Booking;
import com.example.DALN.model.Court;
import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SupportMessageRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final BookingRepository bookingRepository;
    private final SupportMessageRepository supportMessageRepository;

    public AdminController(UserRepository userRepository,
                           CourtRepository courtRepository,
                           BookingRepository bookingRepository,
                           SupportMessageRepository supportMessageRepository) {
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.bookingRepository = bookingRepository;
        this.supportMessageRepository = supportMessageRepository;
    }

    // 1. TỔNG QUAN (DASHBOARD)
    @GetMapping({"", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");

        long totalCourts = courtRepository.count();
        long totalUsers = userRepository.count();
        List<Booking> allBookings = bookingRepository.findAll();

        // Tính tổng doanh thu giao dịch (loại trừ đơn hủy hoàn cọc)
        double totalRevenue = allBookings.stream()
                .filter(b -> !"CANCELLED_REFUND".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        // Lọc số đơn đặt trong ngày hôm nay
        LocalDate today = LocalDate.now();
        long todayBookingsCount = allBookings.stream()
                .filter(b -> today.equals(b.getBookingDate()))
                .count();

        // Đẩy số liệu vào Model cho 4 thẻ thống kê
        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("todayBookingsCount", todayBookingsCount);
        model.addAttribute("totalCourts", totalCourts);
        model.addAttribute("totalUsers", totalUsers);

        // Lấy danh sách đơn gần đây (mới nhất lên đầu)
        allBookings.sort((b1, b2) -> {
            if (b1.getCreatedAt() == null || b2.getCreatedAt() == null) return 0;
            return b2.getCreatedAt().compareTo(b1.getCreatedAt());
        });
        model.addAttribute("recentBookings", allBookings.stream().limit(10).toList());

        return "admin/dashboard";
    }

    // 2. QUẢN LÝ SÂN BÃI
    @GetMapping("/courts")
    public String courtsManagement(Model model) {
        model.addAttribute("activePage", "courts");
        List<Court> courts = courtRepository.findAll();
        model.addAttribute("courts", courts);
        return "admin/courts";
    }

    // Phê duyệt cụm sân
    @PostMapping("/courts/{id}/approve")
    public String approveCourt(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        courtRepository.findById(id).ifPresent(c -> {
            c.setStatus("ACTIVE");
            courtRepository.save(c);
            redirectAttributes.addFlashAttribute("successMessage", "Đã phê duyệt cụm sân '" + c.getName() + "' lên sàn thành công!");
        });
        return "redirect:/admin/courts";
    }

    // Từ chối cụm sân
    @PostMapping("/courts/{id}/reject")
    public String rejectCourt(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        courtRepository.findById(id).ifPresent(c -> {
            c.setStatus("REJECTED");
            courtRepository.save(c);
            redirectAttributes.addFlashAttribute("errorMessage", "Đã từ chối cụm sân '" + c.getName() + "'.");
        });
        return "redirect:/admin/courts";
    }

    // Xóa cụm sân
    @PostMapping("/courts/{id}/delete")
    public String deleteCourt(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            courtRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã xóa cụm sân khỏi hệ thống!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Không thể xóa cụm sân này vì đã phát sinh đơn đặt!");
        }
        return "redirect:/admin/courts";
    }

    // 3. ĐƠN ĐẶT SÂN
    @GetMapping("/bookings")
    public String bookingsManagement(Model model) {
        model.addAttribute("activePage", "bookings");
        List<Booking> bookings = bookingRepository.findAll();
        bookings.sort((b1, b2) -> {
            if (b1.getCreatedAt() == null || b2.getCreatedAt() == null) return 0;
            return b2.getCreatedAt().compareTo(b1.getCreatedAt());
        });
        model.addAttribute("bookings", bookings);
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

    // 5. BÁO CÁO DOANH THU & HOA HỒNG SÀN (8%)
    @GetMapping("/reports")
    public String reportsManagement(Model model) {
        model.addAttribute("activePage", "reports");
        List<Booking> allBookings = bookingRepository.findAll();

        double grossTotal = allBookings.stream()
                .filter(b -> !"CANCELLED_REFUND".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        double platformCommission = grossTotal * 0.08;
        double ownerPayout = grossTotal - platformCommission;

        model.addAttribute("grossTotal", grossTotal);
        model.addAttribute("platformCommission", platformCommission);
        model.addAttribute("ownerPayout", ownerPayout);
        model.addAttribute("bookings", allBookings);

        return "admin/reports";
    }

    // 6. CÀI ĐẶT HỆ THỐNG
    @GetMapping("/settings")
    public String settingsManagement(Model model) {
        model.addAttribute("activePage", "settings");
        return "admin/settings";
    }

    // 7. KÊNH HỖ TRỢ & CHAT VỚI CHỦ SÂN
    // Mở phòng chat với một Chủ Sân cụ thể khi bấm nút "Nhắn tin"
    @GetMapping("/support/chat/{ownerId}")
    public String adminChatWithOwner(@PathVariable Long ownerId, Model model) {
        model.addAttribute("activePage", "users");
        User targetOwner = userRepository.findById(ownerId).orElse(null);
        if (targetOwner == null) {
            return "redirect:/admin/users?tab=owners";
        }

        model.addAttribute("targetOwner", targetOwner);
        List<SupportMessage> messages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(ownerId);
        model.addAttribute("messages", messages);

        return "admin/chat";
    }

    // Tiếp nhận tin nhắn Admin gửi phản hồi cho Chủ Sân
    @PostMapping("/support/chat/{ownerId}/send")
    @ResponseBody
    public String adminSendMessage(@PathVariable Long ownerId,
                                   @RequestParam String content,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || content == null || content.trim().isEmpty()) {
            return "error";
        }

        User admin = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        User targetOwner = userRepository.findById(ownerId).orElse(null);

        if (admin == null || targetOwner == null) {
            return "error";
        }

        SupportMessage message = SupportMessage.builder()
                .owner(targetOwner)
                .sender(admin)
                .senderRole("ADMIN")
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();
        supportMessageRepository.save(message);

        return "success";
    }

    // API lấy dữ liệu tin nhắn dạng JSON phục vụ Polling cập nhật tự động
    @GetMapping("/support/chat/{ownerId}/messages")
    @ResponseBody
    public List<OwnerController.SupportMessageDTO> getAdminChatMessages(@PathVariable Long ownerId) {
        return supportMessageRepository.findByOwnerIdOrderBySentAtAsc(ownerId).stream()
                .map(m -> new OwnerController.SupportMessageDTO(
                        m.getId(), 
                        m.getSenderRole(), 
                        m.getContent(), 
                        m.getSentAt() != null ? m.getSentAt().toString() : ""
                ))
                .collect(Collectors.toList());
    }
}