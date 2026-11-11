package com.example.DALN.controller;

import com.example.DALN.model.Court;
import com.example.DALN.model.SubCourt;
import com.example.DALN.model.SupportMessage;
import com.example.DALN.model.User;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SubCourtRepository;
import com.example.DALN.repository.SupportMessageRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final CourtRepository courtRepository;
    private final SubCourtRepository subCourtRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final UserRepository userRepository;

    public HomeController(CourtRepository courtRepository, 
                         SubCourtRepository subCourtRepository,
                         SupportMessageRepository supportMessageRepository,
                         UserRepository userRepository) {
        this.courtRepository = courtRepository;
        this.subCourtRepository = subCourtRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String home(Model model) {
        // Lấy danh sách các cụm sân đã được duyệt (ACTIVE) hoặc hiển thị lên trang chủ
        List<Court> favoriteCourts = courtRepository.findAll().stream()
                .filter(c -> "ACTIVE".equalsIgnoreCase(c.getStatus()) || c.getStatus() == null)
                .collect(Collectors.toList());
        model.addAttribute("favoriteCourts", favoriteCourts);
        return "index";
    }

    @GetMapping("/ban-do")
    public String mapPage() {
        return "map";
    }

    // Trang chi tiết giới thiệu thông tin cụm sân cho khách hàng
    @GetMapping("/courts/{id}")
    public String courtDetail(@PathVariable Long id, Model model) {
        Court court = courtRepository.findById(id).orElse(null);
        if (court == null) {
            return "redirect:/"; // Nếu không tìm thấy sân thì quay về trang chủ
        }

        // Chặn nếu sân chưa được duyệt ACTIVE
        if (court.getStatus() != null && !"ACTIVE".equalsIgnoreCase(court.getStatus())) {
            return "redirect:/";
        }

        model.addAttribute("court", court);
        
        // Lấy danh sách các sân con trực thuộc cụm sân này để khách hàng xem và đặt lịch
        List<SubCourt> subCourts = subCourtRepository.findAll().stream()
                .filter(sc -> sc.getCourt() != null && sc.getCourt().getId().equals(id))
                .collect(Collectors.toList());
        model.addAttribute("subCourts", subCourts);

        return "court-detail"; // Trả về file giao diện court-detail.html
    }

    // ==========================================
    // CÁC ĐƯỜNG DẪN XỬ LÝ CHAT TRỰC TUYẾN TRÊN WEB
    // ==========================================

    // 1. Mở trang giao diện khung chat giữa khách và chủ sân
    @GetMapping("/chat/court/{courtId}")
    public String clientChatWithCourtOwner(@PathVariable Long courtId, Model model, @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return "redirect:/login"; // Chưa đăng nhập thì chuyển hướng về trang đăng nhập
        }

        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null || court.getOwner() == null) {
            return "redirect:/";
        }

        User owner = court.getOwner();
        model.addAttribute("court", court);
        model.addAttribute("owner", owner);
        
        // Lấy lịch sử tin nhắn của chủ sân này
        List<SupportMessage> messages = supportMessageRepository.findByOwnerIdOrderBySentAtAsc(owner.getId());
        model.addAttribute("messages", messages != null ? messages : Collections.emptyList());

        return "client/chat"; // Trỏ đến file templates/client/chat.html
    }

    // 2. API nhận tin nhắn gửi từ khách hàng
    @PostMapping("/chat/court/{courtId}/send")
    @ResponseBody
    public String clientSendMessage(@PathVariable Long courtId,
                                    @RequestParam String content,
                                    @AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null || content == null || content.trim().isEmpty()) {
            return "error";
        }

        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null || court.getOwner() == null) {
            return "error";
        }

        User sender = userRepository.findByUsername(userDetails.getUsername()).orElse(null);
        if (sender == null) {
            return "error";
        }

        SupportMessage message = SupportMessage.builder()
                .owner(court.getOwner())
                .sender(sender)
                .senderRole("USER") // Xác định đây là tin nhắn của khách hàng
                .content(content.trim())
                .sentAt(LocalDateTime.now())
                .build();
        supportMessageRepository.save(message);

        return "success";
    }

    // 3. API trả về danh sách tin nhắn dạng JSON để client tự động cập nhật
    @GetMapping("/chat/court/{courtId}/messages")
    @ResponseBody
    public List<SupportMessageDTO> getClientChatMessages(@PathVariable Long courtId) {
        Court court = courtRepository.findById(courtId).orElse(null);
        if (court == null || court.getOwner() == null) {
            return Collections.emptyList();
        }

        return supportMessageRepository.findByOwnerIdOrderBySentAtAsc(court.getOwner().getId()).stream()
                .map(m -> new SupportMessageDTO(
                        m.getId(), 
                        m.getSenderRole(), 
                        m.getContent(), 
                        m.getSentAt() != null ? m.getSentAt().toString() : ""
                ))
                .collect(Collectors.toList());
    }

    // DTO nội bộ phục vụ trả về dữ liệu tin nhắn qua JSON
    public static class SupportMessageDTO {
        private Long id;
        private String senderRole;
        private String content;
        private String sentAt;

        public SupportMessageDTO(Long id, String senderRole, String content, String sentAt) {
            this.id = id;
            this.senderRole = senderRole;
            this.content = content;
            this.sentAt = sentAt;
        }

        public Long getId() { return id; }
        public String getSenderRole() { return senderRole; }
        public String getContent() { return content; }
        public String getSentAt() { return sentAt; }
    }
}