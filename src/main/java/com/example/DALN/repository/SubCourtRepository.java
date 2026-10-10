package com.example.DALN.repository;

import com.example.DALN.model.SubCourt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SubCourtRepository extends JpaRepository<SubCourt, Long> {
    List<SubCourt> findByCourtId(Long courtId);
}