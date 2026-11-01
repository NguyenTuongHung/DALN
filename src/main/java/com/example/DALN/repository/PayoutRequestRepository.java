package com.example.DALN.repository;

import com.example.DALN.model.PayoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {
    List<PayoutRequest> findByOwnerIdOrderByRequestedAtDesc(Long ownerId);
}