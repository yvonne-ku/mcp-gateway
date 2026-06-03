package com.noinch.mcp.protocol.core.mcp.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义 MCP Tool 注解
 * 标注在方法上，表示该方法是一个 MCP 工具
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface McpTool {

    /** 工具名称，默认使用方法名 */
    String name() default "";

    /** 工具描述 */
    String description() default "";
}
