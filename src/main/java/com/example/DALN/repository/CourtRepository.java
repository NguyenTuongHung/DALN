
package com.example.DALN.repository;

import com.example.DALN.model.Court;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CourtRepository extends JpaRepository<Court, Long> {

    // Tìm kiếm theo môn thể thao
    List<Court> findBySportTypeIgnoreCase(String sportType);

    // Tìm kiếm nâng cao theo tên sân, địa chỉ và môn thể thao
    @Query("SELECT c FROM Court c WHERE " +
           "(:keyword IS NULL OR :keyword = '' " +
           "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(c.address) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (" +
           ":sportType IS NULL OR :sportType = '' OR LOWER(:sportType) = 'all' " +

           // Cầu lông
           "OR (LOWER(:sportType) = 'badminton' " +
           "AND LOWER(c.sportType) = 'cầu lông') " +

           // Bóng rổ
           "OR (LOWER(:sportType) = 'basketball' " +
           "AND LOWER(c.sportType) = 'bóng rổ') " +

           // Các môn thể thao khác
           "OR LOWER(c.sportType) = LOWER(:sportType)" +
           ")")
    List<Court> searchCourts(
            @Param("keyword") String keyword,
            @Param("sportType") String sportType
    );
}
