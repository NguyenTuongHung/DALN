package com.example.DALN.config;

import com.example.DALN.model.Court;
import com.example.DALN.model.SubCourt;
import com.example.DALN.model.User;
import com.example.DALN.repository.CourtRepository;
import com.example.DALN.repository.SubCourtRepository;
import com.example.DALN.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig implements WebMvcConfigurer {

    // Cấu hình cho phép truy cập các file ảnh đã upload trong thư mục uploads/
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Mã hóa mật khẩu bảo mật BCrypt
    }

    @Bean
public UserDetailsService userDetailsService(UserRepository userRepository) {

    return username -> userRepository.findByUsername(username)
        .map(user -> {

            // Lấy role từ database
            String role = user.getRole();

            if (role == null || role.isBlank()) {
                role = "ROLE_USER";
            }

            role = role.trim().toUpperCase();

            // Đảm bảo role luôn có tiền tố ROLE_
            if (!role.startsWith("ROLE_")) {
                role = "ROLE_" + role;
            }

            boolean isAccountLocked =
                    "LOCKED".equalsIgnoreCase(user.getStatus());

            // Sử dụng authorities để giữ nguyên ROLE_USER, ROLE_OWNER...
            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getUsername())
                    .password(user.getPassword())
                    .authorities(role)
                    .accountLocked(isAccountLocked)
                    .disabled(isAccountLocked)
                    .build();

        })
        .orElseThrow(() ->
            new UsernameNotFoundException(
                "Không tìm thấy user: " + username
            )
        );
}
    
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
        .csrf(csrf -> csrf.disable())

        .authorizeHttpRequests(auth -> auth

            // ADMIN
            .requestMatchers("/admin/**")
            .hasAuthority("ROLE_ADMIN")

            // CHỦ SÂN
            .requestMatchers("/owner/**")
            .hasAuthority("ROLE_OWNER")

            // KHÁCH HÀNG
            .requestMatchers("/customer/**")
            .hasAuthority("ROLE_USER")

            // Trang công khai
            .requestMatchers(
                "/",
                "/login",
                "/register",
                "/css/**",
                "/js/**",
                "/images/**",
                "/uploads/**",
                "/api/**",
                "/tim-san",
                "/san-bong/**",
                "/dat-san/**",
                "/ban-do"
            ).permitAll()

            // Trang yêu cầu đăng nhập
            .requestMatchers(
                "/profile/**",
                "/lich-su-dat-san",
                "/lich-su-hoat-dong",
                "/doi-mat-khau",
                "/bookings/**"
            ).authenticated()

            .anyRequest().authenticated()
        )

        .formLogin(login -> login
            .loginPage("/login")
            .loginProcessingUrl("/perform_login")

            .successHandler((request, response, authentication) -> {

                var authorities = authentication.getAuthorities();

                if (authorities.stream().anyMatch(
                        r -> r.getAuthority().equals("ROLE_ADMIN"))) {

                    response.sendRedirect("/admin/dashboard");

                } else if (authorities.stream().anyMatch(
                        r -> r.getAuthority().equals("ROLE_OWNER"))) {

                    response.sendRedirect("/owner/dashboard");

                } else {

                    response.sendRedirect("/");
                }
            })

            .failureUrl("/login?error=true")
            .permitAll()
        )

        .logout(logout -> logout
            .logoutUrl("/logout")
            .logoutSuccessUrl("/login?logout=true")
            .invalidateHttpSession(true)
            .deleteCookies("JSESSIONID")
            .permitAll()
        );

    return http.build();
}

    @Bean
    public CommandLineRunner initData(UserRepository userRepository, 
                                       CourtRepository courtRepository, 
                                       SubCourtRepository subCourtRepository, 
                                       PasswordEncoder passwordEncoder) {
        return args -> {
            // 1. Tạo tài khoản Admin mặc định
            if (!userRepository.existsByUsername("admin")) {
                userRepository.save(User.builder()
                        .username("admin")
                        .password(passwordEncoder.encode("admin123"))
                        .fullName("Quản Trị Viên Sàn")
                        .phone("0987654321")
                        .role("ROLE_ADMIN")
                        .status("ACTIVE")
                        .build());
            }

            // 2. Tạo tài khoản Chủ Sân mẫu
            User owner = userRepository.findByUsername("chusanhung").orElseGet(() -> 
                userRepository.save(User.builder()
                        .username("chusanhung")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Nguyễn Tường Hưng")
                        .phone("0912334455")
                        .role("ROLE_OWNER")
                        .businessName("Cụm Sân Thể Thao Hưng Phát")
                        .bankName("Vietcombank")
                        .bankAccountNumber("1029384756")
                        .bankAccountName("NGUYEN TUONG HUNG")
                        .commissionRate(8.0)
                        .status("ACTIVE")
                        .build())
            );

            // 3. Tạo tài khoản Khách hàng mẫu
            if (!userRepository.existsByUsername("khachhang01")) {
                userRepository.save(User.builder()
                        .username("khachhang01")
                        .password(passwordEncoder.encode("123456"))
                        .fullName("Trần Minh Tuấn")
                        .phone("0988111222")
                        .role("ROLE_USER")
                        .status("ACTIVE")
                        .build());
            }

            // 4. Khởi tạo dữ liệu sân bãi nếu chưa có
            if (courtRepository.count() == 0) {
                Court c1 = courtRepository.save(Court.builder()
                        .name("002 Pickleball Club")
                        .sportType("pickleball")
                        .address("28 Thảo Điền, TP. Thủ Đức, Hồ Chí Minh")
                        .openHours("06:00 - 22:30")
                        .rating(4.9)
                        .reviewCount(56)
                        .pricePerHour(180000.0)
                        .latitude(10.8032)
                        .longitude(106.7321)
                        .imageUrl("https://images.unsplash.com/photo-1554068865-24cecd4e34b8?w=800&auto=format&fit=crop&q=80")
                        .description("Cụm sân Pickleball tiêu chuẩn quốc tế với mặt thảm chống trượt Cushioned Acrylic, đèn LED chuyên dụng không chói mắt và phòng nghỉ tiện nghi.")
                        .amenities("Bãi đỗ xe ô tô, Đèn chiếu sáng đêm, Căng tin nước uống, Cho thuê vợt & bóng")
                        .owner(owner)
                        .build());

                subCourtRepository.save(SubCourt.builder().name("Sân PB 1 (4 người)").court(c1).status("ACTIVE").build());
                subCourtRepository.save(SubCourt.builder().name("Sân PB 2 (4 người)").court(c1).status("ACTIVE").build());

                Court c2 = courtRepository.save(Court.builder()
                        .name("Trung Tâm Sân Bóng Arsenal")
                        .sportType("football")
                        .address("Số 18 Hoàng Diệu, Quận 3, TP. Hồ Chí Minh")
                        .openHours("05:00 - 23:00")
                        .rating(4.8)
                        .reviewCount(112)
                        .pricePerHour(350000.0)
                        .latitude(10.7850)
                        .longitude(106.6870)
                        .imageUrl("https://images.unsplash.com/photo-1529900748604-07564a03e7a6?w=800&auto=format&fit=crop&q=80")
                        .description("Mặt cỏ nhân tạo sợi kim cương nhập khẩu, hệ thống thoát nước hiện đại thi đấu tốt trong mọi thời tiết.")
                        .amenities("Bãi đỗ xe ô tô, Đèn chiếu sáng đêm, Phòng thay đồ & Tắm, Căng tin nước uống")
                        .owner(owner)
                        .build());

                subCourtRepository.save(SubCourt.builder().name("Sân 7 người - Sân A").court(c2).status("ACTIVE").build());
                subCourtRepository.save(SubCourt.builder().name("Sân 7 người - Sân B").court(c2).status("ACTIVE").build());

                Court c3 = courtRepository.save(Court.builder()
                        .name("CLB Cầu Lông ACE Cầu Giấy")
                        .sportType("badminton")
                        .address("Đường Trần Thái Tông, Cầu Giấy, Hà Nội")
                        .openHours("06:00 - 23:00")
                        .rating(4.7)
                        .reviewCount(40)
                        .pricePerHour(120000.0)
                        .latitude(21.0313)
                        .longitude(105.7879)
                        .imageUrl("https://images.unsplash.com/photo-1626224583764-f87db24ac4ea?w=800&auto=format&fit=crop&q=80")
                        .description("Thảm sàn cao su Yonex vân cát đạt chuẩn BWF chống chấn thương khớp gối. Trần cao thoáng mát.")
                        .amenities("Bãi đỗ xe ô tô, Đèn chiếu sáng đêm, Phòng thay đồ & Tắm, Căng tin nước uống")
                        .owner(owner)
                        .build());

                subCourtRepository.save(SubCourt.builder().name("Sân Cầu Lông 1").court(c3).status("ACTIVE").build());
                subCourtRepository.save(SubCourt.builder().name("Sân Cầu Lông 2").court(c3).status("ACTIVE").build());
            }
        };
    }
}