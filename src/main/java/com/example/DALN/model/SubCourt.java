package com.example.DALN.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sub_courts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubCourt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // Sân số 1 (Trong nhà), Sân số 2 (Ngoài trời)...

    @ManyToOne
    @JoinColumn(name = "court_id")
    private Court court;

    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, MAINTENANCE
}