
package com.example.DALN.repository;

import com.example.DALN.model.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository
        extends JpaRepository<Settlement, Long> {

    List<Settlement> findBySettlementMonth(String settlementMonth);

    Optional<Settlement> findByOwnerIdAndSettlementMonth(
            Long ownerId,
            String settlementMonth
    );

    boolean existsByOwnerIdAndSettlementMonth(
            Long ownerId,
            String settlementMonth
    );
}