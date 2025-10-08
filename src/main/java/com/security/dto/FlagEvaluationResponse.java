package com.security.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagEvaluationResponse {
    private String flagKey;
    private boolean enabled;
    private String variation;
    private String variationValue;
    private String reason;
    private String matchedRuleId;
}
