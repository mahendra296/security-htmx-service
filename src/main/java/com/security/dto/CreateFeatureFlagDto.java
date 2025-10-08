package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// CreateFeatureFlagDto.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateFeatureFlagDto {
    private String name;
    private String key;
    private String description;
    private boolean enabled;
    private List<VariationDto> variations;
}
