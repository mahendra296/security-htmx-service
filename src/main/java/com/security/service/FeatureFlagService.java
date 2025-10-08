package com.security.service;

import com.security.dto.*;
import com.security.entity.FeatureFlag;
import com.security.entity.Rule;
import com.security.entity.Variation;
import com.security.repository.FeatureFlagRepository;
import com.security.repository.RuleRepository;
import com.security.repository.VariationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FeatureFlagService {

    private final FeatureFlagRepository featureFlagRepository;
    private final VariationRepository variationRepository;
    private final RuleRepository ruleRepository;

    @Transactional(readOnly = true)
    public List<FeatureFlagDto> getAllFlags() {
        log.info("Fetching all feature flags");
        return featureFlagRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public FeatureFlagDto getFlag(String key) {
        log.info("Fetching feature flag: {}", key);
        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));
        return convertToDto(flag);
    }

    @Transactional
    public FeatureFlagDto createFlag(CreateFeatureFlagDto dto) {
        log.info("Creating feature flag: {}", dto.getKey());

        if (featureFlagRepository.existsByKey(dto.getKey())) {
            throw new RuntimeException("Feature flag with key '" + dto.getKey() + "' already exists");
        }

        FeatureFlag flag = new FeatureFlag();
        flag.setKey(dto.getKey());
        flag.setName(dto.getName());
        flag.setDescription(dto.getDescription());
        flag.setEnabled(dto.isEnabled());
        flag.setVariations(new ArrayList<>());
        flag.setRules(new ArrayList<>());

        flag = featureFlagRepository.save(flag);

        // Add variations
        if (dto.getVariations() != null && !dto.getVariations().isEmpty()) {
            for (int i = 0; i < dto.getVariations().size(); i++) {
                VariationDto vDto = dto.getVariations().get(i);
                Variation variation = new Variation();
                variation.setFeatureFlag(flag);
                variation.setName(vDto.getName());
                variation.setValue(vDto.getValue());
                variation.setIndex(i);
                flag.getVariations().add(variation);
            }
            featureFlagRepository.save(flag);
        }

        log.info("Feature flag created successfully: {}", dto.getKey());
        return convertToDto(flag);
    }

    @Transactional
    public RuleDto createRule(CreateRuleDto dto) {
        log.info("Creating rule for flag: {}", dto.getFlagKey());

        FeatureFlag flag = featureFlagRepository.findByKey(dto.getFlagKey())
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + dto.getFlagKey()));

        if (dto.getVariationIndex() < 0 || dto.getVariationIndex() >= flag.getVariations().size()) {
            throw new RuntimeException("Invalid variation index: " + dto.getVariationIndex());
        }

        Rule rule = new Rule();
        rule.setFeatureFlag(flag);
        rule.setAttribute(dto.getAttribute());
        rule.setOperator(dto.getOperator());
        rule.setValue(dto.getValue());
        rule.setVariationIndex(dto.getVariationIndex());
        rule.setOrder(flag.getRules().size());

        rule = ruleRepository.save(rule);
        flag.getRules().add(rule);

        log.info("Rule created successfully for flag: {}", dto.getFlagKey());
        return convertRuleToDto(rule);
    }

    @Transactional
    public void toggleFlag(String key, boolean enabled) {
        log.info("Toggling flag {} to {}", key, enabled);

        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));

        flag.setEnabled(enabled);
        featureFlagRepository.save(flag);

        log.info("Flag {} toggled to {}", key, enabled);
    }

    @Transactional
    public void deleteRule(String flagKey, String ruleId) {
        log.info("Deleting rule {} from flag {}", ruleId, flagKey);

        FeatureFlag flag = featureFlagRepository.findByKey(flagKey)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + flagKey));

        Long ruleIdLong = Long.parseLong(ruleId);
        Rule rule = ruleRepository.findById(ruleIdLong)
                .orElseThrow(() -> new RuntimeException("Rule not found: " + ruleId));

        if (!rule.getFeatureFlag().getKey().equals(flagKey)) {
            throw new RuntimeException("Rule does not belong to this flag");
        }

        flag.getRules().remove(rule);
        ruleRepository.delete(rule);

        log.info("Rule deleted successfully");
    }

    @Transactional
    public void deleteFlag(String key) {
        log.info("Deleting feature flag: {}", key);

        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));

        featureFlagRepository.delete(flag);
        log.info("Feature flag deleted successfully: {}", key);
    }

    @Transactional(readOnly = true)
    public FlagEvaluationResponse evaluateFlagWithContext(String key, Map<String, String> context) {
        log.info("Evaluating flag: {} with context: {}", key, context);

        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));

        FlagEvaluationResponse response = new FlagEvaluationResponse();
        response.setFlagKey(key);

        // Check if flag is globally disabled
        if (!flag.isEnabled()) {
            response.setEnabled(false);
            response.setReason("flag_disabled");
            setDefaultVariation(flag, response);
            return response;
        }

        // Evaluate rules in order
        List<Rule> sortedRules = flag.getRules().stream()
                .sorted((r1, r2) -> Integer.compare(r1.getOrder(), r2.getOrder()))
                .collect(Collectors.toList());

        for (Rule rule : sortedRules) {
            String contextValue = context.get(rule.getAttribute());

            if (contextValue != null && evaluateCondition(rule, contextValue)) {
                // Rule matched
                Variation matchedVariation = flag.getVariations().stream()
                        .filter(v -> v.getIndex() == rule.getVariationIndex())
                        .findFirst()
                        .orElse(null);

                if (matchedVariation != null) {
                    response.setEnabled(true);
                    response.setVariation(matchedVariation.getName());
                    response.setVariationValue(matchedVariation.getValue());
                    response.setReason("rule_match");
                    response.setMatchedRuleId(rule.getId().toString());

                    log.info("Rule matched for flag {}: rule={}, variation={}",
                            key, rule.getId(), matchedVariation.getName());

                    return response;
                }
            }
        }

        // No rules matched - use default variation
        response.setEnabled(true);
        response.setReason("default_variation");
        setDefaultVariation(flag, response);

        return response;
    }

    @Transactional(readOnly = true)
    public FlagEvaluationResponse evaluateFlagSimple(String key, String attribute, String value) {
        log.info("Simple evaluation for flag: {} with {}={}", key, attribute, value);

        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));

        FlagEvaluationResponse response = new FlagEvaluationResponse();
        response.setFlagKey(key);

        if (!flag.isEnabled()) {
            response.setEnabled(false);
            response.setReason("flag_disabled");
            setDefaultVariation(flag, response);
            return response;
        }

        // Check rules
        for (Rule rule : flag.getRules()) {
            if (rule.getAttribute().equals(attribute) && evaluateCondition(rule, value)) {
                Variation matchedVariation = flag.getVariations().stream()
                        .filter(v -> v.getIndex() == rule.getVariationIndex())
                        .findFirst()
                        .orElse(null);

                if (matchedVariation != null) {
                    response.setEnabled(true);
                    response.setVariation(matchedVariation.getName());
                    response.setVariationValue(matchedVariation.getValue());
                    response.setReason("rule_match");
                    response.setMatchedRuleId(rule.getId().toString());
                    return response;
                }
            }
        }

        response.setEnabled(true);
        response.setReason("default_variation");
        setDefaultVariation(flag, response);

        return response;
    }

    private void setDefaultVariation(FeatureFlag flag, FlagEvaluationResponse response) {
        if (!flag.getVariations().isEmpty()) {
            int defaultIndex = flag.getDefaultVariationIndex() != null ?
                    flag.getDefaultVariationIndex() : 0;

            Variation defaultVariation = flag.getVariations().stream()
                    .filter(v -> v.getIndex() == defaultIndex)
                    .findFirst()
                    .orElse(flag.getVariations().get(0));

            response.setVariation(defaultVariation.getName());
            response.setVariationValue(defaultVariation.getValue());
        }
    }

    public boolean evaluateFlag(String key, String attribute, String value) {
        log.info("Evaluating flag: {} for attribute: {} with value: {}", key, attribute, value);

        FeatureFlag flag = featureFlagRepository.findByKey(key)
                .orElseThrow(() -> new RuntimeException("Feature flag not found: " + key));

        if (!flag.isEnabled()) {
            return false;
        }

        // Check rules in order
        for (Rule rule : flag.getRules()) {
            if (rule.getAttribute().equals(attribute) && evaluateCondition(rule, value)) {
                return true;
            }
        }

        return false;
    }

    private boolean evaluateCondition(Rule rule, String userValue) {
        String ruleValue = rule.getValue();

        switch (rule.getOperator().toLowerCase()) {
            case "equals":
                return userValue.equals(ruleValue);
            case "contains":
                return userValue.contains(ruleValue);
            case "startswith":
                return userValue.startsWith(ruleValue);
            case "endswith":
                return userValue.endsWith(ruleValue);
            case "matches":
                return userValue.matches(ruleValue);
            case "in":
                String[] values = ruleValue.split(",");
                for (String val : values) {
                    if (userValue.equals(val.trim())) {
                        return true;
                    }
                }
                return false;
            default:
                return false;
        }
    }

    private FeatureFlagDto convertToDto(FeatureFlag flag) {
        FeatureFlagDto dto = new FeatureFlagDto();
        dto.setKey(flag.getKey());
        dto.setName(flag.getName());
        dto.setDescription(flag.getDescription());
        dto.setEnabled(flag.isEnabled());

        dto.setVariations(flag.getVariations().stream()
                .sorted((v1, v2) -> Integer.compare(v1.getIndex(), v2.getIndex()))
                .map(this::convertVariationToDto)
                .collect(Collectors.toList()));

        dto.setRules(flag.getRules().stream()
                .sorted((r1, r2) -> Integer.compare(r1.getOrder(), r2.getOrder()))
                .map(this::convertRuleToDto)
                .collect(Collectors.toList()));

        return dto;
    }

    private VariationDto convertVariationToDto(Variation variation) {
        return new VariationDto(variation.getName(), variation.getValue());
    }

    private RuleDto convertRuleToDto(Rule rule) {
        RuleDto dto = new RuleDto();
        dto.setId(rule.getId().toString());
        dto.setAttribute(rule.getAttribute());
        dto.setOperator(rule.getOperator());
        dto.setValue(rule.getValue());
        dto.setVariationIndex(rule.getVariationIndex());
        return dto;
    }
}