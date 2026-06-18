package com.noinch.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String userId;

    private String accessToken;
    private String refreshToken;

    private long accessExpiresIn;
    private long refreshExpiresIn;

    private String tokenType;

    private String role;

    @Builder.Default
    private boolean success = true;
    private String message;
}
