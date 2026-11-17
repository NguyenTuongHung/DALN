
package com.example.DALN.controller;

import com.example.DALN.model.Product;
import com.example.DALN.repository.ProductRepository;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Controller
public class MarketplaceController implements WebMvcConfigurer {

    private final ProductRepository productRepository;

    public MarketplaceController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // Thư mục lưu ảnh sản phẩm
    private static final Path UPLOAD_DIR =
            Paths.get("uploads").toAbsolutePath().normalize();

    // Cho phép trình duyệt truy cập ảnh trong thư mục uploads
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(UPLOAD_DIR.toUri().toString());
    }

    // 1. Trang chợ sản phẩm
    @GetMapping("/mua-ban")
    public String marketplacePage(Model model) {

        List<Product> productList =
                productRepository.findByStatusIgnoreCaseOrderByCreatedAtDesc("ACTIVE");

        model.addAttribute("products", productList);

        return "marketplace";
    }

    // 2. Trang chi tiết sản phẩm
    @GetMapping("/mua-ban/chi-tiet")
    public String productDetail(
            @RequestParam("id") Long id,
            Model model,
            RedirectAttributes redirectAttributes) {

        return productRepository.findById(id)
                .map(product -> {
                    model.addAttribute("product", product);
                    return "marketplace-detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute(
                            "errorMessage", "Không tìm thấy sản phẩm!"
                    );
                    return "redirect:/mua-ban";
                });
    }

    // 3. Trang đăng bán
    @GetMapping("/mua-ban/dang-ban")
    public String postProductPage() {
        return "marketplace-post";
    }

    // 4. Xử lý đăng bán sản phẩm
    @PostMapping(
            value = "/mua-ban/dang-ban",
            consumes = "multipart/form-data"
    )
    public String handlePostProduct(
            @RequestParam("title") String title,
            @RequestParam("sportType") String sportType,
            @RequestParam("price") Double price,
            @RequestParam(value = "imageFile", required = false)
                    MultipartFile imageFile,
            @RequestParam("description") String description,
            @RequestParam("location") String location,
            @RequestParam("phone") String phone,
            RedirectAttributes redirectAttributes) {

        try {

            // Kiểm tra dữ liệu
            if (title.isBlank()
                    || sportType.isBlank()
                    || description.isBlank()
                    || location.isBlank()
                    || phone.isBlank()
                    || price <= 0) {

                redirectAttributes.addFlashAttribute(
                        "errorMessage", "Vui lòng nhập đầy đủ thông tin hợp lệ!"
                );

                return "redirect:/mua-ban/dang-ban";
            }

            Product product = new Product();

            product.setTitle(title.trim());
            product.setSportType(sportType);
            product.setPrice(price);
            product.setDescription(description.trim());
            product.setLocation(location.trim());
            product.setPhone(phone.trim());
            product.setStatus("ACTIVE");
            product.setCreatedAt(LocalDateTime.now());

            // Xử lý ảnh
            if (imageFile != null && !imageFile.isEmpty()) {

                if (imageFile.getContentType() == null
                        || !imageFile.getContentType().startsWith("image/")) {

                    redirectAttributes.addFlashAttribute(
                            "errorMessage", "File tải lên phải là hình ảnh!"
                    );

                    return "redirect:/mua-ban/dang-ban";
                }

                Files.createDirectories(UPLOAD_DIR);

                String originalName = imageFile.getOriginalFilename();

                String extension = ".jpg";

                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(
                            originalName.lastIndexOf(".")
                    ).toLowerCase();
                }

                String fileName = UUID.randomUUID() + extension;

                Path filePath = UPLOAD_DIR.resolve(fileName).normalize();

                if (!filePath.startsWith(UPLOAD_DIR)) {
                    throw new IOException("Đường dẫn file không hợp lệ!");
                }

                Files.copy(
                        imageFile.getInputStream(),
                        filePath,
                        StandardCopyOption.REPLACE_EXISTING
                );

                product.setImageUrl("/uploads/" + fileName);

            } else {
                product.setImageUrl("/images/default-product.png");
            }

            // Lưu sản phẩm vào MySQL
            productRepository.saveAndFlush(product);

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "Đăng bán sản phẩm thành công!"
            );

            return "redirect:/mua-ban";

        } catch (Exception e) {

            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "Đăng bán thất bại: " + e.getMessage()
            );

            return "redirect:/mua-ban/dang-ban";
        }
    }

    // 5. Giỏ hàng
    @GetMapping("/gio-hang")
    public String cartPage() {
        return "cart";
    }

    // 6. Thanh toán
    @PostMapping("/gio-hang/thanh-toan")
    public String processCheckout() {
        return "redirect:/checkout/success";
    }

    // 7. Thành công
    @GetMapping("/checkout/success")
    public String checkoutSuccessPage() {
        return "order-success";
    }

    // 8. Lịch sử hoạt động
    @GetMapping("/lich-su-hoat-dong")
    public String historyPage() {
        return "lich-su";
    }
}
