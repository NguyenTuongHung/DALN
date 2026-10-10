package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "courts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;              // VD: 002 Pickleball Club

    private String sportType;         // football, pickleball, badminton, tennis, basketball
    private String address;           // Phường Linh Trung, TP. Thủ Đức, Hồ Chí Minh
    private String openHours;         // 06:00 - 22:30
    private Double rating;            // 4.9
    private Integer reviewCount;      // 56
    private Double pricePerHour;      // 180000.0
    private String imageUrl;          // Link ảnh sân thật
    private String description;       // Giới thiệu sân, mặt sân tiêu chuẩn...
    private String amenities;         // Bãi đỗ xe ô tô, Đèn chiếu sáng, Căng tin, Phòng thay đồ

    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<SubCourt> subCourts;  // Các sân nhỏ bên trong
}