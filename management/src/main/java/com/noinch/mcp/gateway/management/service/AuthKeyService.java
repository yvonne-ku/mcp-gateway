package com.noinch.mcp.gateway.management.service;

import com.noinch.mcp.gateway.core.dto.AuthKeyApplyRequest;
import com.noinch.mcp.gateway.core.dto.AuthKeyApplyResponse;
import com.noinch.mcp.gateway.core.dto.BatchAuthKeyApplyRequest;
import com.noinch.mcp.gateway.core.dto.BatchAuthKeyApplyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AuthKeyService {
    Mono<AuthKeyApplyResponse> applyAuthKey(AuthKeyApplyRequest request);
    Mono<BatchAuthKeyApplyResponse> batchApplyAuthKeys(BatchAuthKeyApplyRequest request);
    Flux<AuthKeyApplyResponse> getUserAuthKeys(String userId);
    Mono<Page<AuthKeyApplyResponse>> getAllAuthKeys(String userId, String serviceId, Boolean isActive, Pageable pageable);
    Mono<Void> revokeAuthKey(Long keyId);
    Mono<AuthKeyApplyResponse> updateKeyStatus(Long keyId, Boolean isActive);
    Mono<AuthKeyApplyResponse> renewAuthKey(Long keyId, long extendHours);
    Mono<Integer> revokeUserServiceKeys(String userId, String serviceId);
}