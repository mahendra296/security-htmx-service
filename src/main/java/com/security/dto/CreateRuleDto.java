package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// CreateRuleDto.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRuleDto {
    private String flagKey;
    private String attribute;
    private String operator;
    private String value;
    private int variationIndex;
}
