package com.noinch.mcp.gateway.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String userId;

    private String password; // reserved for future credential-based auth
}
