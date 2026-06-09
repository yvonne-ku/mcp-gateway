package com.noinch.mcp.gateway.auth.service;

import reactor.core.publisher.Mono;

public interface TokenRedisService {

    /**
     * logout 后将 token 加入 redis 黑名单，TTL 与 token 剩余时间相同
     */
    Mono<Void> blacklistToken(String token, long ttlSeconds);

    /**
     * 存储新的 refreshToken 到 redis 白名单
     */
    Mono<Void> saveRefreshToken(String token, long ttlSeconds);

    /**
     * 从 redis 白名单删除旧的 refreshToken
     */
    Mono<Void> deleteRefreshToken(String token);

    /**
     * 检验 accessToken 是否在 redis 黑名单
     */
    Mono<Boolean> isAccessTokenBlacklisted(String token);

    /**
     * 检验 refreshToken 是否在 redis 黑名单
     */
    Mono<Boolean> isRefreshTokenWhitelisted(String token);
}
