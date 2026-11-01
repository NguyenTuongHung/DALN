package com.example.DALN.controller;

import com.example.DALN.model.Court;
import com.example.DALN.model.SubCourt;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SubCourtRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.stream.Collectors;

@Controller
public class HomeController {

    private final CourtRepository courtRepository;
    private final SubCourtRepository subCourtRepository;

    public HomeController(CourtRepository courtRepository, SubCourtRepository subCourtRepository) {
        this.courtRepository = courtRepository;
        this.subCourtRepository = subCourtRepository;
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

        model.addAttribute("court", court);
        
        // Lấy danh sách các sân con trực thuộc cụm sân này để khách hàng xem và đặt lịch
        List<SubCourt> subCourts = subCourtRepository.findAll().stream()
                .filter(sc -> sc.getCourt() != null && sc.getCourt().getId().equals(id))
                .collect(Collectors.toList());
        model.addAttribute("subCourts", subCourts);

        return "court-detail"; // Trả về file giao diện court-detail.html
    }
}