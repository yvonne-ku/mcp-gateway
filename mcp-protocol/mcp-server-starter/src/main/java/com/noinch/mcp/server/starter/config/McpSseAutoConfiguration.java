package com.noinch.mcp.server.starter.config;

import com.noinch.mcp.protocol.core.mcp.registry.McpToolRegistry;
import com.noinch.mcp.server.starter.McpSseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.util.ClassUtils;

/**
 * MCP Server SSE 自动配置
 * <p> 工作内容：
 * 创建注册中心 {@link McpToolRegistry} 的 Bean
 * 自动扫描所有 {@code @McpTool} 方法并注册到 {@link McpToolRegistry}，
 * 同时注册 {@link McpSseController} 处理 SSE 连接和 JSON-RPC 消息。
 * <p> 功能实现：
 * 1. 基于 JSON-RPC 2.0
 * 2. 支持 SSE 连接
 * 3. 支持 工具的注册和调用
 * <p> 依赖：
 * spring-boot-starter-web (Servlet 容器)
 */
@Slf4j
@AutoConfiguration
@ConditionalOnWebApplication(type = Type.SERVLET)
@EnableConfigurationProperties(McpServerProperties.class)
public class McpSseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public McpToolRegistry mcpToolRegistry(ApplicationContext applicationContext) {
        McpToolRegistry registry = new McpToolRegistry();

        // 拿到 Spring 容器中所有 Bean 的名字（包括你写的 @Service、@Component 等）
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = applicationContext.getBean(beanName);

            // 如果 Bean 被 AOP 代理了（比如 @Transactional），getUserClass() 会拿到原始的目标类而非代理类
            Class<?> beanClass = ClassUtils.getUserClass(bean);

            // registry 会扫描 beanClass 中的所有方法，如果发现有 @McpTool 注解，就注册到 registry 中
            registry.registerTools(bean, beanClass);
        }
        if (registry.size() > 0) {
            log.info("Auto-registered {} MCP tools: {}", registry.size(), registry.getToolNames());
        }
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSseController mcpSseController(McpToolRegistry toolRegistry, McpServerProperties properties) {
        return new McpSseController(toolRegistry, properties);
    }
}
