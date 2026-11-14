package com.example.DALN.repository;

import com.example.DALN.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportMessageRepository
        extends JpaRepository<SupportMessage, Long> {

    // ================================================
    // 1. LẤY TOÀN BỘ TIN NHẮN CỦA MỘT CHỦ SÂN
    // ================================================

    List<SupportMessage> findByOwnerIdOrderBySentAtAsc(
            Long ownerId
    );

    // ================================================
    // 2. LỌC THEO CHỦ SÂN VÀ LOẠI HỘI THOẠI
    // ADMIN_SUPPORT hoặc CUSTOMER_CHAT
    // ================================================

    List<SupportMessage> findByOwnerIdAndChatTypeOrderBySentAtAsc(
            Long ownerId,
            String chatType
    );

    // ================================================
    // 3. LỌC CHÍNH XÁC THEO CHỦ SÂN + KHÁCH HÀNG
    // + LOẠI HỘI THOẠI
    // ================================================

    List<SupportMessage> findByOwnerIdAndCustomerIdAndChatTypeOrderBySentAtAsc(
            Long ownerId,
            Long customerId,
            String chatType
    );

    // ================================================
    // 4. LẤY TIN NHẮN CỦA MỘT KHÁCH HÀNG
    // THEO LOẠI HỘI THOẠI
    // ================================================

    List<SupportMessage> findByCustomerIdAndChatTypeOrderBySentAtAsc(
            Long customerId,
            String chatType
    );

    // ================================================
    // 5. KIỂM TRA CUỘC TRÒ CHUYỆN ĐÃ TỒN TẠI
    // ================================================

    boolean existsByOwnerIdAndCustomerIdAndChatType(
            Long ownerId,
            Long customerId,
            String chatType
    );

    // ================================================
    // 6. BỔ SUNG: XỬ LÝ THÔNG BÁO CHƯA ĐỌC CHO CUSTOMER
    // ================================================

    // Đếm tổng số tin nhắn chưa đọc của một khách hàng cụ thể trong luồng CUSTOMER_CHAT
    // (Tính các tin nhắn do OWNER gửi đến customer mà customer chưa đọc)
    long countByCustomerIdAndChatTypeAndSenderRoleAndIsReadFalse(
            Long customerId, 
            String chatType, 
            String senderRole
    );

    // Cập nhật tất cả tin nhắn từ OWNER gửi tới CUSTOMER trong một cuộc hội thoại cụ thể thành "đã đọc" (isRead = true)
    @Modifying
    @Query("UPDATE SupportMessage m SET m.isRead = true " +
           "WHERE m.customer.id = :customerId " +
           "AND m.owner.id = :ownerId " +
           "AND m.chatType = :chatType " +
           "AND m.senderRole = 'OWNER' " +
           "AND m.isRead = false")
    void markMessagesAsReadForCustomer(
            @Param("customerId") Long customerId, 
            @Param("ownerId") Long ownerId, 
            @Param("chatType") String chatType
    );
}