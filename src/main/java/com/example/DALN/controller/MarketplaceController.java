package com.example.DALN.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MarketplaceController {

    @GetMapping("/mua-ban")
    public String marketplacePage() {
        return "marketplace"; 
    }

    // Xử lý trang chi tiết kèm theo ID sản phẩm
    @GetMapping("/mua-ban/chi-tiet")
    public String productDetail(@RequestParam(name = "id", defaultValue = "1") Long id, Model model) {
        // Bạn có thể dựa vào id để truyền dữ liệu động tương ứng ra giao diện
        model.addAttribute("productId", id);
        return "marketplace-detail";
    }

    @GetMapping("/mua-ban/dang-ban")
    public String postProductPage() {
        return "marketplace-post";
    }
    
    @GetMapping("/gio-hang")
    public String cartPage() {
        return "cart";
    }

    // --- BỔ SUNG XỬ LÝ THANH TOÁN ---

    // Xử lý khi người dùng bấm nút "Xác Nhận Đặt Hàng & Thanh Toán" từ trang giỏ hàng
    @PostMapping("/gio-hang/thanh-toan")
    public String processCheckout() {
        // 1. Thực hiện logic lưu đơn hàng vào Database, trừ tồn kho, v.v. tại đây (nếu có)
        
        // 2. Chuyển hướng sang trang thông báo đặt hàng thành công
        return "redirect:/checkout/success";
    }

    // Hiển thị trang thông báo đặt hàng thành công
    @GetMapping("/checkout/success")
    public String checkoutSuccessPage() {
        return "order-success"; // Trỏ tới file order-success.html trong thư mục templates
    }

    // ---------------------------------

    @GetMapping("/lich-su-hoat-dong")
    public String historyPage() {
        return "lich-su";
    }
}