package com.noinch.mcp.gateway.auth.service.impl;

import com.noinch.mcp.gateway.auth.service.AuthApiKeyService;
import com.noinch.mcp.gateway.core.entity.AuthKeyEntity;
import com.noinch.mcp.gateway.persist.mapper.AuthApiKeyMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@AllArgsConstructor
public class AuthApiKeyServiceImpl implements AuthApiKeyService {

    private final AuthApiKeyMapper authApiKeyMapper;

    @Override
    public AuthKeyEntity validateAuthKey(String authKey) {
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalArgumentException("API key is missing");
        }

        AuthKeyEntity keyEntity = authApiKeyMapper.findByKeyHash(authKey);
        if (keyEntity == null || !keyEntity.getIsActive()) {
            log.warn("Invalid or expired API key");
            throw new IllegalArgumentException("Invalid or expired API key");
        }

        try {
            authApiKeyMapper.updateLastUsedTime(authKey);
        } catch (Exception e) {
            log.warn("Failed to update API key lastUsedTime", e);
        }

        return keyEntity;
    }
}
