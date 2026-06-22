package com.noinch.mcp.gateway.auth.service;

import com.noinch.mcp.gateway.core.entity.AuthKeyEntity;

public interface AuthApiKeyService {

    AuthKeyEntity validateAuthKey(String authKey);
}
