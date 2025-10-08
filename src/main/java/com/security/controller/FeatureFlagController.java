package com.security.controller;

import com.security.dto.*;
import com.security.service.FeatureFlagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/feature-flags")
public class FeatureFlagController {

    private final FeatureFlagService featureFlagService;

    @GetMapping
    @ResponseBody
    public String getAllFlags() {
        log.info("Fetching all feature flags");
        List<FeatureFlagDto> flags = featureFlagService.getAllFlags();

        if (flags.isEmpty()) {
            return "<div class='alert alert-info'>No feature flags found. Create your first flag!</div>";
        }

        StringBuilder html = new StringBuilder("<div class='flag-list'>");

        for (FeatureFlagDto flag : flags) {
            html.append("<div class='flag-item'>");
            html.append("<div class='flag-header'>");
            html.append("<div>");
            html.append("<div class='flag-name'>").append(escapeHtml(flag.getName())).append("</div>");
            html.append("<div class='flag-key'>").append(escapeHtml(flag.getKey())).append("</div>");
            if (flag.getDescription() != null && !flag.getDescription().isEmpty()) {
                html.append("<div style='color: #6c757d; margin-top: 5px;'>")
                        .append(escapeHtml(flag.getDescription())).append("</div>");
            }
            html.append("</div>");

            html.append("<div style='display: flex; gap: 10px; align-items: center;'>");
            html.append("<label class='toggle-switch'>");
            html.append("<input type='checkbox' ")
                    .append(flag.isEnabled() ? "checked" : "")
                    .append(" onchange='toggleFlag(\"").append(flag.getKey()).append("\", this.checked)'>");
            html.append("<span class='slider'></span>");
            html.append("</label>");
            html.append("<button class='btn btn-danger btn-small' onclick='deleteFlag(\"")
                    .append(flag.getKey()).append("\")'>Delete Flag</button>");
            html.append("</div>");
            html.append("</div>");

            // Variations
            html.append("<div style='margin-top: 10px;'>");
            html.append("<strong>Variations:</strong> ");
            for (int i = 0; i < flag.getVariations().size(); i++) {
                VariationDto v = flag.getVariations().get(i);
                if (i > 0) html.append(", ");
                html.append(escapeHtml(v.getName())).append(" (").append(escapeHtml(v.getValue())).append(")");
            }
            html.append("</div>");

            // Rules section
            html.append("<div class='rule-section'>");
            html.append("<div style='display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px;'>");
            html.append("<strong>Targeting Rules</strong>");
            html.append("<button class='btn btn-primary btn-small' onclick='openRuleModal(\"")
                    .append(flag.getKey()).append("\", ")
                    .append(toJsonArray(flag.getVariations())).append(")'>Add Rule</button>");
            html.append("</div>");

            if (flag.getRules() == null || flag.getRules().isEmpty()) {
                html.append("<div style='color: #6c757d; font-style: italic;'>No targeting rules defined</div>");
            } else {
                for (RuleDto rule : flag.getRules()) {
                    html.append("<div class='rule-item'>");
                    html.append("<div class='rule-condition'>");
                    html.append("IF <strong>").append(escapeHtml(rule.getAttribute())).append("</strong> ");
                    html.append(escapeHtml(rule.getOperator())).append(" ");
                    html.append("<strong>").append(escapeHtml(rule.getValue())).append("</strong>");
                    html.append("</div>");

                    VariationDto servedVariation = flag.getVariations().get(rule.getVariationIndex());
                    html.append("<div class='rule-variation'>");
                    html.append("SERVE: ").append(escapeHtml(servedVariation.getName()))
                            .append(" (").append(escapeHtml(servedVariation.getValue())).append(")");
                    html.append("</div>");

                    html.append("<button class='btn btn-danger btn-small' onclick='deleteRule(\"")
                            .append(flag.getKey()).append("\", \"")
                            .append(rule.getId()).append("\")'>Delete</button>");
                    html.append("</div>");
                }
            }
            html.append("</div>");
            html.append("</div>");
        }

        html.append("</div>");
        return html.toString();
    }

    @PostMapping
    @ResponseBody
    public ResponseEntity<String> createFlag(@ModelAttribute CreateFeatureFlagDto dto) {
        log.info("Creating feature flag: {}", dto.getKey());
        try {
            featureFlagService.createFlag(dto);
            return ResponseEntity.ok()
                    .header("HX-Trigger", "flagsUpdated")
                    .body("<div class='alert alert-success'>Feature flag created successfully!</div>");
        } catch (Exception e) {
            log.error("Error creating feature flag", e);
            return ResponseEntity.badRequest()
                    .body("<div class='alert alert-danger'>Error: " + escapeHtml(e.getMessage()) + "</div>");
        }
    }

    @PostMapping("/{flagKey}/rules")
    @ResponseBody
    public ResponseEntity<String> createRule(
            @PathVariable String flagKey,
            @ModelAttribute CreateRuleDto dto) {
        log.info("Creating rule for flag: {}", flagKey);
        try {
            dto.setFlagKey(flagKey);
            featureFlagService.createRule(dto);
            return ResponseEntity.ok()
                    .header("HX-Trigger", "flagsUpdated")
                    .body("<div class='alert alert-success'>Rule created successfully!</div>");
        } catch (Exception e) {
            log.error("Error creating rule", e);
            return ResponseEntity.badRequest()
                    .body("<div class='alert alert-danger'>Error: " + escapeHtml(e.getMessage()) + "</div>");
        }
    }

    @PatchMapping("/{flagKey}/toggle")
    @ResponseBody
    public ResponseEntity<?> toggleFlag(
            @PathVariable String flagKey,
            @RequestBody ToggleFlagDto dto) {
        log.info("Toggling flag: {} to {}", flagKey, dto.isEnabled());
        try {
            featureFlagService.toggleFlag(flagKey, dto.isEnabled());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error toggling flag", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{flagKey}/rules/{ruleId}")
    @ResponseBody
    public ResponseEntity<?> deleteRule(
            @PathVariable String flagKey,
            @PathVariable String ruleId) {
        log.info("Deleting rule {} from flag {}", ruleId, flagKey);
        try {
            featureFlagService.deleteRule(flagKey, ruleId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting rule", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{flagKey}")
    @ResponseBody
    public ResponseEntity<?> deleteFlag(@PathVariable String flagKey) {
        log.info("Deleting flag: {}", flagKey);
        try {
            featureFlagService.deleteFlag(flagKey);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error deleting flag", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{flagKey}")
    @ResponseBody
    public ResponseEntity<FeatureFlagDto> getFlag(@PathVariable String flagKey) {
        log.info("Fetching flag: {}", flagKey);
        try {
            FeatureFlagDto flag = featureFlagService.getFlag(flagKey);
            return ResponseEntity.ok(flag);
        } catch (Exception e) {
            log.error("Error fetching flag", e);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Evaluate a feature flag based on user context
     * POST /api/feature-flags/{flagKey}/evaluate
     * <p>
     * Request body example:
     * {
     * "context": {
     * "email": "user@example.com",
     * "country": "US",
     * "userId": "12345",
     * "plan": "premium"
     * }
     * }
     */
    @PostMapping("/{flagKey}/evaluate")
    @ResponseBody
    public ResponseEntity<FlagEvaluationResponse> evaluateFlag(
            @PathVariable String flagKey,
            @RequestBody FlagEvaluationRequest request) {

        log.info("Evaluating flag: {} with context: {}", flagKey, request.getContext());

        try {
            FlagEvaluationResponse response = featureFlagService.evaluateFlagWithContext(
                    flagKey,
                    request.getContext()
            );

            log.info("Flag {} evaluated: enabled={}, variation={}",
                    flagKey, response.isEnabled(), response.getVariation());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flagKey, e);

            // Return default disabled state on error
            FlagEvaluationResponse errorResponse = new FlagEvaluationResponse();
            errorResponse.setEnabled(false);
            errorResponse.setFlagKey(flagKey);
            errorResponse.setReason("error: " + e.getMessage());

            return ResponseEntity.ok(errorResponse);
        }
    }

    /**
     * Simplified evaluation - checks if flag is enabled for a single attribute
     * GET /api/feature-flags/{flagKey}/evaluate?attribute=email&value=user@example.com
     */
    @GetMapping("/{flagKey}/evaluate")
    @ResponseBody
    public ResponseEntity<FlagEvaluationResponse> evaluateFlagSimple(
            @PathVariable String flagKey,
            @RequestParam String attribute,
            @RequestParam String value) {

        log.info("Simple evaluation for flag: {} with {}={}", flagKey, attribute, value);

        try {
            FlagEvaluationResponse response = featureFlagService.evaluateFlagSimple(
                    flagKey,
                    attribute,
                    value
            );

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error evaluating flag: {}", flagKey, e);

            FlagEvaluationResponse errorResponse = new FlagEvaluationResponse();
            errorResponse.setEnabled(false);
            errorResponse.setFlagKey(flagKey);
            errorResponse.setReason("error: " + e.getMessage());

            return ResponseEntity.ok(errorResponse);
        }
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String toJsonArray(List<VariationDto> variations) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < variations.size(); i++) {
            if (i > 0) json.append(",");
            json.append("{\"name\":\"").append(escapeHtml(variations.get(i).getName()))
                    .append("\",\"value\":\"").append(escapeHtml(variations.get(i).getValue()))
                    .append("\"}");
        }
        json.append("]");
        return json.toString();
    }
}