package com.example.DALN.controller;

import com.example.DALN.model.PayoutRequest;
import com.example.DALN.repository.PayoutRequestRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminPayoutController {

    private final PayoutRequestRepository payoutRequestRepository;

    public AdminPayoutController(PayoutRequestRepository payoutRequestRepository) {
        this.payoutRequestRepository = payoutRequestRepository;
    }

    // Hiển thị danh sách tất cả các yêu cầu quyết toán/rút tiền của các chủ sân
    @GetMapping("/payouts")
    public String adminPayoutsPage(Model model) {
        List<PayoutRequest> payoutRequests = payoutRequestRepository.findAll();
        model.addAttribute("payoutRequests", payoutRequests);
        return "admin/payouts"; // Trỏ đến file templates/admin/payouts.html
    }

    // Xử lý khi Admin bấm nút Phê duyệt chuyển khoản
    @PostMapping("/payouts/approve/{id}")
    public String approvePayout(@PathVariable Long id) {
        PayoutRequest request = payoutRequestRepository.findById(id).orElse(null);
        if (request != null) {
            request.setStatus("COMPLETED"); // Cập nhật trạng thái thành Đã chuyển khoản
            payoutRequestRepository.save(request);
        }
        return "redirect:/admin/payouts";
    }
}