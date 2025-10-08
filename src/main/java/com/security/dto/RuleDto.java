package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// RuleDto.java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RuleDto {
    private String id;
    private String attribute;
    private String operator;
    private String value;
    private int variationIndex;
}
