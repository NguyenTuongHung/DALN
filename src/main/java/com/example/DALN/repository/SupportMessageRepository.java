
package com.example.DALN.repository;

import com.example.DALN.model.SupportMessage;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
