package com.example.DALN.repository;

import com.example.DALN.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {
    // Lấy toàn bộ tin nhắn thuộc về chủ sân theo thứ tự thời gian tăng dần
    List<SupportMessage> findByOwnerIdOrderBySentAtAsc(Long ownerId);
}