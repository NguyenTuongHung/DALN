package com.example.DALN.controller;

import com.example.DALN.model.User;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("user", new User());
        return "register";
    }

    @PostMapping("/register")
    public String processRegister(@ModelAttribute User user, Model model) {
        // 1. Kiểm tra xem tên đăng nhập / email đã tồn tại trong database chưa
        if (userRepository.existsByUsername(user.getUsername())) {
            model.addAttribute("error", "Tên đăng nhập hoặc Email đã tồn tại!");
            return "register";
        }

        // 2. Mã hóa mật khẩu cực kỳ an toàn bằng BCrypt
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        // 3. Xử lý phân quyền linh hoạt theo lựa chọn từ giao diện (ROLE_USER hoặc ROLE_OWNER)
        if (user.getRole() == null || user.getRole().trim().isEmpty()) {
            user.setRole("ROLE_USER"); // Mặc định nếu không chọn gì
        }

        // 4. Gán trạng thái tài khoản kích hoạt mặc định
        user.setStatus("ACTIVE");

        // 5. Lưu vào Database
        userRepository.save(user);

        // Chuyển hướng về trang login kèm thông báo đăng ký thành công
        return "redirect:/login?registered=true";
    }
}