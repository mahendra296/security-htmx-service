package com.security.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "rules")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "feature_flag_id", nullable = false)
    private FeatureFlag featureFlag;

    @Column(nullable = false)
    private String attribute;

    @Column(nullable = false)
    private String operator;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String value;

    @Column(name = "variation_index")
    private int variationIndex;

    @Column(name = "rule_order")
    private int order;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
