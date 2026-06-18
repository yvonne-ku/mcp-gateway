package com.noinch.mcp.gateway.auth.service.impl;

import com.noinch.mcp.gateway.auth.service.AuthApiKeyService;
import com.noinch.mcp.gateway.persist.mapper.AuthApiKeyMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Slf4j
@Service
@AllArgsConstructor
public class AuthApiKeyServiceImpl implements AuthApiKeyService {

    private final AuthApiKeyMapper authApiKeyMapper;

    @Override
    public void validateAuthKey(String authKey) {
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalArgumentException("API key is missing");
        }

        String hash = sha256(authKey);
        if (!authApiKeyMapper.isValidKey(hash)) {
            log.warn("Invalid or expired API key: hash={}", hash);
            throw new IllegalArgumentException("Invalid or expired API key");
        }

        try {
            authApiKeyMapper.updateLastUsedTime(hash);
        } catch (Exception e) {
            log.warn("Failed to update API key lastUsedTime", e);
        }
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
