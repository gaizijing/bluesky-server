package com.bluesky.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class RuleSetRequest {

    @NotBlank
    private String name;
    private Map<String, Object> rules;
    private Boolean enableLlm;
}
