package com.noinch.mcp.gateway.core.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class MCPServiceUpdateRequest {
    private String name;
    private String description;
    private String endpoint;
    private String status;

    @Positive(message = "Max QPS must be positive")
    private Integer maxQps;

    private String healthCheckUrl;
    private String documentation;
}