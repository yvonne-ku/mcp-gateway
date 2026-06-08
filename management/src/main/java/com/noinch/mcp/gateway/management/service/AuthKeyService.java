package com.noinch.mcp.gateway.management.service;

import com.noinch.mcp.gateway.core.dto.AuthKeyApplyRequest;
import com.noinch.mcp.gateway.core.dto.AuthKeyResponse;
import com.noinch.mcp.gateway.core.dto.BatchAuthKeyApplyRequest;
import com.noinch.mcp.gateway.core.dto.BatchAuthKeyApplyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthKeyService {
    Mono<AuthKeyResponse> applyAuthKey(AuthKeyApplyRequest request);
    Mono<BatchAuthKeyApplyResponse> batchApplyAuthKeys(BatchAuthKeyApplyRequest request);
    Flux<AuthKeyResponse> getUserAuthKeys(String userId);
    Mono<Page<AuthKeyResponse>> getAllAuthKeys(String userId, String serviceId, Boolean isActive, Pageable pageable);
    Mono<Void> revokeAuthKey(Long keyId);
    Mono<AuthKeyResponse> updateKeyStatus(Long keyId, Boolean isActive);
    Mono<AuthKeyResponse> renewAuthKey(Long keyId, long extendHours);
    Mono<Integer> revokeUserServiceKeys(String userId, String serviceId);
}