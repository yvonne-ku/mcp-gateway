package com.noinch.mcp.protocol.core.mcp.registry;

import com.noinch.mcp.protocol.core.mcp.annotation.McpTool;
import com.noinch.mcp.protocol.core.mcp.model.ToolDefinition;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 工具注册表
 * 通过反射扫描对象上的 @McpTool 注解，构建 name → Method 的映射
 */
public class McpToolRegistry {

    private final Map<String, McpToolEntry> toolMap = new LinkedHashMap<>();

    /**
     * 注册一个对象上的所有 @McpTool 方法
     */
    public void registerTools(Object bean, Class<?> beanClass) {
        for (Method method : beanClass.getDeclaredMethods()) {
            McpTool annotation = method.getAnnotation(McpTool.class);
            if (annotation != null) {
                String name = annotation.name().isEmpty() ? method.getName() : annotation.name();
                String description = annotation.description();
                method.setAccessible(true);
                toolMap.put(name, new McpToolEntry(name, description, method, bean));
            }
        }
    }

    /**
     * 根据名称查找工具
     */
    public Optional<McpToolEntry> getTool(String name) {
        return Optional.ofNullable(toolMap.get(name));
    }

    /**
     * 获取所有工具定义
     */
    public List<ToolDefinition> listToolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (McpToolEntry entry : toolMap.values()) {
            definitions.add(ToolDefinition.builder()
                    .name(entry.name)
                    .description(entry.description)
                    .inputSchema(generateInputSchema(entry.method))
                    .build());
        }
        return definitions;
    }

    /**
     * 根据方法参数生成 JSON Schema（inputSchema）
     * 示例输出：
     * {
     *   "type": "object",
     *   "properties": {
     *     "deviceId": {"type": "string", "description": "设备ID"},
     *     "action": {"type": "string", "description": "操作"}
     *   },
     *   "required": ["deviceId", "action"]
     * }
     */
    private Map<String, Object> generateInputSchema(Method method) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : method.getParameters()) {
            String paramName = param.getName();
            properties.put(paramName, Map.of(
                    "type", mapJavaTypeToJsonType(param.getType()),
                    "description", paramName
            ));
            required.add(paramName);
        }

        schema.put("properties", properties);
        schema.put("required", required);
        return schema;
    }

    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == int.class || javaType == Integer.class
                || javaType == long.class || javaType == Long.class) return "number";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        return "string";
    }

    public int size() {
        return toolMap.size();
    }

    public Set<String> getToolNames() {
        return toolMap.keySet();
    }

    /**
     * 工具注册项
     */
    public static class McpToolEntry {
        public final String name;
        public final String description;
        public final Method method;
        public final Object bean;

        public McpToolEntry(String name, String description, Method method, Object bean) {
            this.name = name;
            this.description = description;
            this.method = method;
            this.bean = bean;
        }

        /**
         * 调用工具方法
         */
        public Object invoke(Map<String, Object> arguments) throws Exception {
            Parameter[] parameters = method.getParameters();
            Object[] args = new Object[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                String paramName = parameters[i].getName();
                Object value = arguments.get(paramName);
                if (value == null && parameters[i].getType().isPrimitive()) {
                    throw new IllegalArgumentException("Missing required parameter: " + paramName);
                }
                args[i] = convertValue(value, parameters[i].getType());
            }
            return method.invoke(bean, args);
        }

        private Object convertValue(Object value, Class<?> targetType) {
            if (value == null) return null;
            if (targetType.isInstance(value)) return value;
            if (targetType == int.class || targetType == Integer.class) {
                return ((Number) value).intValue();
            }
            if (targetType == long.class || targetType == Long.class) {
                return ((Number) value).longValue();
            }
            if (targetType == boolean.class || targetType == Boolean.class) {
                return Boolean.valueOf(value.toString());
            }
            return value.toString();
        }
    }
}
