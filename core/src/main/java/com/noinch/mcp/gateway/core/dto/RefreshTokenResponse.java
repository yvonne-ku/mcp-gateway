package com.noinch.mcp.gateway.core.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RefreshTokenResponse {

    private String userId;

    private String accessToken;
    private String refreshToken;

    private long accessExpiresIn;
    private long refreshExpiresIn;

    private String tokenType;

    @Builder.Default
    private boolean success = true;
    private String message;
}
