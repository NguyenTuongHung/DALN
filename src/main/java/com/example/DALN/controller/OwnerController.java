package com.example.DALN.controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.example.DALN.model.Booking;
import com.example.DALN.model.Court;
import com.example.DALN.model.PayoutRequest;
import com.example.DALN.model.SubCourt;
import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
import com.example.DALN.repository.BookingRepository;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.PayoutRequestRepository;
import com.example.DALN.repository.SubCourtRepository;
import com.example.DALN.repository.SupportMessageRepository;
import com.example.DALN.repository.UserRepository;

@Controller
@RequestMapping("/owner")
public class OwnerController {

    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final SubCourtRepository subCourtRepository;
    private final BookingRepository bookingRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final PayoutRequestRepository payoutRequestRepository;

    // Thư mục lưu trữ ảnh upload của cụm sân
    private static final String UPLOAD_DIR = "uploads/courts/";

    public OwnerController(UserRepository userRepository,
                           CourtRepository courtRepository,
                           SubCourtRepository subCourtRepository,
                           BookingRepository bookingRepository,
                           SupportMessageRepository supportMessageRepository,
                           PayoutRequestRepository payoutRequestRepository) {
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.subCourtRepository = subCourtRepository;
        this.bookingRepository = bookingRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.payoutRequestRepository = payoutRequestRepository;
    }

    private User getAuthenticatedOwner(UserDetails userDetails) {
        if (userDetails == null) return null;
        return userRepository.findByUsername(userDetails.getUsername()).orElse(null);
    }

    // 1. DASHBOARD: LỊCH SÂN & ĐÓN KHÁCH (CHỈ NẠP CÁC SÂN ĐÃ ACTIVE ĐỂ ĐIỀU HÀNH)
    @GetMapping({"", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        model.addAttribute("owner", owner);
        model.addAttribute("activeMenu", "dashboard");

        // Đồng bộ owner cho các sân mẫu nếu thiếu
        List<Court> allCourts = courtRepository.findAll();
        for (Court c : allCourts) {
            if (c.getOwner() == null) {
                c.setOwner(owner);
                if (c.getStatus() == null) {
                    c.setStatus("ACTIVE");
                }
                courtRepository.save(c);
            }
        }

        // Lấy danh sách sân của chủ sân (ưu tiên sân đã ACTIVE để xếp lịch đón khách)
        List<Court> myCourts = courtRepository.findAll().stream()
                .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                .collect(Collectors.toList());
        model.addAttribute("myCourts", myCourts);

        List<Long> myCourtIds = myCourts.stream().map(Court::getId).collect(Collectors.toList());

        // Lấy tất cả đơn đặt thuộc các cụm sân này
        List<Booking> myBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getCourt() != null && myCourtIds.contains(b.getCourt().getId()))
                .sorted((b1, b2) -> {
                    if (b1.getCreatedAt() == null || b2.getCreatedAt() == null) return 0;
                    return b2.getCreatedAt().compareTo(b1.getCreatedAt());
                })
                .collect(Collectors.toList());

        model.addAttribute("todayBookings", myBookings);

        // Tính doanh thu từ tổng các đơn hàng hợp lệ (Bỏ qua đơn hủy và đơn chủ sân tự khóa)
        double todayGross = myBookings.stream()
                .filter(b -> !"CANCELLED_REFUND".equalsIgnoreCase(b.getPaymentStatus()))
                .filter(b -> !"LOCKED_BY_OWNER".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        double commissionRate = (owner.getCommissionRate() != null) ? owner.getCommissionRate() : 8.0;
        double netRevenue = todayGross * (1.0 - (commissionRate / 100.0));

        model.addAttribute("todayGross", todayGross);
        model.addAttribute("netRevenue", netRevenue);

        return "owner/dashboard";
    }

    // 2. CHECK-IN ĐÓN KHÁCH
    @PostMapping("/bookings/{id}/check-in")
    public String checkInCustomer(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Booking booking = bookingRepository.findById(id).orElse(null);
        if (booking != null) {
            booking.setPaymentStatus("COMPLETED");
            bookingRepository.save(booking);
            redirectAttributes.addFlashAttribute("successMessage", "Xác nhận đón khách thành công cho mã đơn #" + booking.getBookingCode());
        }
        return "redirect:/owner/dashboard";
    }

    // 3. KẾT NỐI SÂN VỚI SÀN (TRẠNG THÁI PENDING CHỜ DUYỆT)
    @GetMapping("/courts/connect")
    public String connectCourtPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        model.addAttribute("owner", owner);
        model.addAttribute("activeMenu", "connect");

        // Lấy tất cả cụm sân của chủ sân (sắp xếp ID mới nhất lên trước để dễ theo dõi)
        List<Court> myCourts = courtRepository.findAll().stream()
                .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                .sorted((c1, c2) -> c2.getId().compareTo(c1.getId()))
                .collect(Collectors.toList());
        model.addAttribute("myCourts", myCourts);

        return "owner/connect-court";
    }

    @PostMapping("/courts/connect")
    public String handleRegisterNewCourt(@RequestParam String courtName,
                                       @RequestParam String sportType,
                                       @RequestParam String address,
                                       @RequestParam String openHours,
                                       @RequestParam Double pricePerHour,
                                       @RequestParam(required = false) String description,
                                       @RequestParam int numberOfSubCourts,
                                       @RequestParam("imageFile") MultipartFile imageFile,
                                       @AuthenticationPrincipal UserDetails userDetails,
                                       RedirectAttributes redirectAttributes) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        // Xử lý lưu file ảnh tải lên
        String imageUrl = "https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=800&auto=format&fit=crop&q=80"; // Ảnh mặc định phòng hờ
        if (imageFile != null && !imageFile.isEmpty()) {
            try {
                File uploadDir = new File(UPLOAD_DIR);
                if (!uploadDir.exists()) {
                    uploadDir.mkdirs();
                }
                String fileName = System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                Path filePath = Paths.get(UPLOAD_DIR + fileName);
                Files.copy(imageFile.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
                
                // Đường dẫn URL để truy cập ảnh từ trình duyệt thông qua ResourceHandler
                imageUrl = "/uploads/courts/" + fileName;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Tạo cụm sân mới với trạng thái PENDING để Admin sàn kiểm duyệt
        Court newCourt = Court.builder()
                .name(courtName)
                .sportType(sportType)
                .address(address)
                .openHours(openHours)
                .pricePerHour(pricePerHour)
                .description(description)
                .status("PENDING") // Chờ duyệt
                .latitude(10.8032)
                .longitude(106.7321)
                .rating(5.0)
                .reviewCount(0)
                .imageUrl(imageUrl)
                .owner(owner)
                .build();
        Court savedCourt = courtRepository.save(newCourt);

        // Khởi tạo các sân con trực thuộc
        for (int i = 1; i <= numberOfSubCourts; i++) {
            subCourtRepository.save(SubCourt.builder()
                    .name("Sân " + i)
                    .court(savedCourt)
                    .status("ACTIVE")
                    .build());
        }

        redirectAttributes.addFlashAttribute("successMessage", 
            "Đã gửi hồ sơ cụm sân '" + courtName + "' tới Admin sàn thành công! Hồ sơ đang ở trạng thái 'Chờ duyệt'.");
        return "redirect:/owner/courts/connect";
    }

    // 4. KHÓA GIỜ THỦ CÔNG
    @PostMapping("/courts/toggle-slot")
    public String toggleSlot(@RequestParam Long subCourtId,
                           @RequestParam String date,
                           @RequestParam String timeSlot,
                           @RequestParam(required = false, defaultValue = "Khóa bảo trì") String reason,
                           @AuthenticationPrincipal UserDetails userDetails) {
        User owner = getAuthenticatedOwner(userDetails);
        SubCourt subCourt = subCourtRepository.findById(subCourtId).orElse(null);

        if (subCourt != null && owner != null) {
            Booking lockedSlot = Booking.builder()
                    .bookingCode("LOCK-" + (System.currentTimeMillis() % 10000))
                    .court(subCourt.getCourt())
                    .subCourt(subCourt)
                    .user(owner)
                    .customerName("Chủ Sân Khóa")
                    .customerPhone(owner.getPhone())
                    .customerNote(reason)
                    .bookingDate(LocalDate.parse(date))
                    .timeSlot(timeSlot)
                    .totalPrice(0.0)
                    .depositAmount(0.0)
                    .remainingAmount(0.0)
                    .paymentStatus("LOCKED_BY_OWNER")
                    .createdAt(LocalDateTime.now())
                    .build();
            bookingRepository.save(lockedSlot);
        }
        return "redirect:/owner/dashboard";
    }

    // 5. CÀI ĐẶT GIÁ SÂN (HIỂN THỊ TRANG)
    @GetMapping("/pricing")
    public String pricingPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User owner = getAuthenticatedOwner(userDetails);
        model.addAttribute("owner", owner);
        model.addAttribute("activeMenu", "pricing");

        List<Court> myCourts = (owner != null)
                ? courtRepository.findAll().stream()
                    .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                    .collect(Collectors.toList())
                : List.of();
        model.addAttribute("myCourts", myCourts);

        return "owner/pricing";
    }

    // 5.1. XỬ LÝ LƯU CẬP NHẬT BẢNG GIÁ THEO KHUNG GIỜ
    @PostMapping("/pricing/update")
    public String updateCourtPricing(@RequestParam Long courtId,
                                     @RequestParam Double normalPrice,
                                     @RequestParam Double peakPrice,
                                     @RequestParam Double weekendPrice,
                                     @AuthenticationPrincipal UserDetails userDetails,
                                     RedirectAttributes redirectAttributes) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        Court court = courtRepository.findById(courtId).orElse(null);
        if (court != null && court.getOwner() != null && court.getOwner().getId().equals(owner.getId())) {
            court.setPricePerHour(normalPrice);
            courtRepository.save(court);

            redirectAttributes.addFlashAttribute("successMessage", 
                "Đã cập nhật bảng giá thành công cho cụm sân '" + court.getName() + "'!");
        } else {
            redirectAttributes.addFlashAttribute("successMessage", "Không tìm thấy cụm sân hoặc bạn không có quyền chỉnh sửa!");
        }

        return "redirect:/owner/pricing";
    }

    // 6. QUYẾT TOÁN & VÍ TIỀN
    @GetMapping("/payout")
    public String payoutPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User owner = getAuthenticatedOwner(userDetails);
        model.addAttribute("owner", owner);
        model.addAttribute("activeMenu", "payout");

        if (owner == null) return "redirect:/login";

        List<Court> myCourts = courtRepository.findAll().stream()
                .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                .collect(Collectors.toList());
        List<Long> myCourtIds = myCourts.stream().map(Court::getId).collect(Collectors.toList());

        List<Booking> allOwnerBookings = bookingRepository.findAll().stream()
                .filter(b -> b.getCourt() != null && myCourtIds.contains(b.getCourt().getId()))
                .collect(Collectors.toList());

        double commissionRate = (owner.getCommissionRate() != null) ? owner.getCommissionRate() : 8.0;
        double completedGross = allOwnerBookings.stream()
                .filter(b -> "COMPLETED".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();
        double availableBalance = completedGross * (1.0 - (commissionRate / 100.0));

        double pendingHold = allOwnerBookings.stream()
                .filter(b -> "PAID_DEPOSIT".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getDepositAmount() != null ? b.getDepositAmount() : 0.0)
                .sum();

        model.addAttribute("walletBalance", availableBalance);
        model.addAttribute("pendingHold", pendingHold);

        // Lấy lịch sử các yêu cầu rút tiền của chủ sân này từ database
        List<PayoutRequest> payoutHistory = payoutRequestRepository.findByOwnerIdOrderByRequestedAtDesc(owner.getId());
        model.addAttribute("payoutHistory", payoutHistory);

        return "owner/payout";
    }

    // 6.1. XỬ LÝ GỬI YÊU CẦU RÚT TIỀN (CHUYỂN KHOẢN)
    @PostMapping("/payout/request")
    public String requestPayout(@AuthenticationPrincipal UserDetails userDetails, 
                                RedirectAttributes redirectAttributes) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        List<Court> myCourts = courtRepository.findAll().stream()
                .filter(c -> c.getOwner() != null && c.getOwner().getId().equals(owner.getId()))
                .collect(Collectors.toList());
        List<Long> myCourtIds = myCourts.stream().map(Court::getId).collect(Collectors.toList());

        double completedGross = bookingRepository.findAll().stream()
                .filter(b -> b.getCourt() != null && myCourtIds.contains(b.getCourt().getId()))
                .filter(b -> "COMPLETED".equalsIgnoreCase(b.getPaymentStatus()))
                .mapToDouble(b -> b.getTotalPrice() != null ? b.getTotalPrice() : 0.0)
                .sum();

        double commissionRate = (owner.getCommissionRate() != null) ? owner.getCommissionRate() : 8.0;
        double availableBalance = completedGross * (1.0 - (commissionRate / 100.0));

        if (availableBalance <= 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Số dư khả dụng của bạn không đủ để tạo yêu cầu rút tiền!");
            return "redirect:/owner/payout";
        }

        PayoutRequest request = PayoutRequest.builder()
                .transactionCode("#PAY-" + (int)(Math.random() * 9000 + 1000))
                .amount(availableBalance)
                .bankInfo((owner.getBankName() != null ? owner.getBankName() : "VCB") + " - " + (owner.getBankAccountNumber() != null ? owner.getBankAccountNumber() : "1029384756"))
                .status("PENDING")
                .requestedAt(LocalDateTime.now())
                .owner(owner)
                .build();

        payoutRequestRepository.save(request);

        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi yêu cầu rút tiền thành công! Admin sàn sẽ xử lý và chuyển khoản trong 24h tới.");
        return "redirect:/owner/payout";
    }

    // 7. HỖ TRỢ TỪ ADMIN (KẾT NỐI CHAT THẬT)
    @GetMapping("/support")
    public String supportPage(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return "redirect:/login";

        model.addAttribute("owner", owner);
        model.addAttribute("activeMenu", "support");

        List<SupportMessage> messages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(owner.getId());
        model.addAttribute("messages", messages);
        return "owner/support";
    }

    // API gửi tin nhắn từ Chủ Sân
    @PostMapping("/support/send")
    @ResponseBody
    public String sendMessageFromOwner(@RequestParam String content,
                                       @AuthenticationPrincipal UserDetails userDetails) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null || content == null || content.trim().isEmpty()) {
            return "error";
        }

        SupportMessage message = SupportMessage.builder()
                .owner(owner)
                .sender(owner)
                .senderRole("OWNER")
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();
        supportMessageRepository.save(message);

        return "success";
    }

    // API lấy danh sách tin nhắn để JavaScript tự động cập nhật thời gian thực
    @GetMapping("/support/messages")
    @ResponseBody
    public List<SupportMessageDTO> getOwnerMessages(@AuthenticationPrincipal UserDetails userDetails) {
        User owner = getAuthenticatedOwner(userDetails);
        if (owner == null) return List.of();

        return supportMessageRepository.findByOwnerIdOrderBySentAtAsc(owner.getId()).stream()
                .map(m -> new SupportMessageDTO(m.getId(), m.getSenderRole(), m.getContent(), m.getSentAt().toString()))
                .collect(Collectors.toList());
    }

    public record SupportMessageDTO(Long id, String senderRole, String content, String sentAt) {}
}