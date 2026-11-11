package com.example.DALN.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/lich-su-hoat-dong")
public String historyPage() {
    return "lich-su";
}
}