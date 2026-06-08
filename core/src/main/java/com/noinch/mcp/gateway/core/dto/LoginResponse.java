package com.noinch.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
    private String token;
    private String userId;
    private long expiresIn;
    private String tokenType;
    @Builder.Default
    private boolean success = true;
    private String message;
}
