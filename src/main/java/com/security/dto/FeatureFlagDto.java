package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// FeatureFlagDto.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeatureFlagDto {
    private String key;
    private String name;
    private String description;
    private boolean enabled;
    private List<VariationDto> variations;
    private List<RuleDto> rules;
    private String defaultVariation;
}

