
package com.example.DALN.service;

import com.example.DALN.model.User;
import com.example.DALN.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User registerUser(
            String username,
            String rawPassword,
            String fullName,
            String phone) {

        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException(
                    "Vui lòng nhập tên đăng nhập.");
        }

        if (rawPassword == null || rawPassword.length() < 6) {
            throw new IllegalArgumentException(
                    "Mật khẩu phải có ít nhất 6 ký tự.");
        }

        username = username.trim();

        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException(
                    "Tên đăng nhập đã tồn tại.");
        }

        User user = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .fullName(fullName)
                .phone(phone)
                .role("ROLE_USER")
                .status("ACTIVE")
                .build();

        return userRepository.save(user);
    }
}