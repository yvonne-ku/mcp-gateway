package com.noinch.mcp.gateway.core.tool;

import com.noinch.mcp.gateway.core.entity.AuthKeyEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;

public class AuthKeyGenerator {

    // 项目固定密钥盐
    private static final String SECRET_SALT = "MySuperSecretSalt";
    private static final String ALG_NAME = "HmacSHA256";

    public static String generateKey(String userId, String serviceId) {
        try {
            String data = userId + ":" + serviceId + ":" + System.currentTimeMillis();

            // Mac 是一个提供各种哈希算法的工具包
            // 通过指定哈希算法名可以取到对应的算法工具
            // 再用 salt 进行进一步的加强
            Mac sha256_HMAC = Mac.getInstance(ALG_NAME);
            SecretKeySpec secretKeySpec = new SecretKeySpec(SECRET_SALT.getBytes(StandardCharsets.UTF_8), ALG_NAME);
            sha256_HMAC.init(secretKeySpec);

            // 生成
            byte[] hashBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));

            // 使用 Base64 编码，将 Hash 值转化为固定长度编码
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error while generating auth key", e);
        }
    }

    /**
     * 构建一个实体（默认永不过期）
     */
    public static AuthKeyEntity buildAuthKeyEntity(String userId, String serviceId) {
        String key = generateKey(userId, serviceId);

        return AuthKeyEntity.builder()
                .keyHash(key)   // 存储 Key（生产可换成 hash）
                .userId(userId)
                .MCPServiceId(serviceId)
                .expiresAt(null)   // 默认永不过期
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .build();
    }

    /**
     * 构建一个带有效期的实体（可选）
     */
    public static AuthKeyEntity buildAuthKeyEntityWithExpiry(String userId, String serviceId, long expireHours) {
        String key = generateKey(userId, serviceId);

        return AuthKeyEntity.builder()
                .keyHash(key)
                .userId(userId)
                .MCPServiceId(serviceId)
                .expiresAt(LocalDateTime.now().plusHours(expireHours))
                .createdAt(LocalDateTime.now())
                .isActive(true)
                .build();
    }
}
