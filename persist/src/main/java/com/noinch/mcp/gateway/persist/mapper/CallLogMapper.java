package com.noinch.mcp.gateway.persist.mapper;

import com.noinch.mcp.gateway.core.dto.StatsSummary;
import com.noinch.mcp.gateway.core.entity.CallLogEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CallLogMapper {

    /**
     * 插入调用日志
     */
    @Insert("""
        INSERT INTO api_call_logs (user_id, service_id, auth_key_id, request_path, 
                                  request_method, client_ip, user_agent, status_code, 
                                  response_time_ms, error_message, created_at)
        VALUES (#{userId}, #{serviceId}, #{authKeyId}, #{requestPath}, 
                #{requestMethod}, #{clientIp}, #{userAgent}, #{statusCode},
                #{responseTimeMs}, #{errorMessage}, #{createdAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(CallLogEntity callLogEntity);

    /**
     * 按条件查询调用统计（聚合）
     */
    @Select({"<script>",
            "SELECT service_id, user_id, COUNT(*) AS total_calls,",
            "       SUM(CASE WHEN status_code >= 200 AND status_code &lt; 300 THEN 1 ELSE 0 END) AS success_calls,",
            "       SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END) AS failed_calls,",
            "       COALESCE(AVG(response_time_ms), 0) AS avg_response_time_ms,",
            "       COALESCE(MAX(response_time_ms), 0) AS max_response_time_ms",
            "FROM api_call_logs",
            "<where>",
            "  <if test='userId != null'>AND user_id = #{userId}</if>",
            "  <if test='serviceId != null'>AND service_id = #{serviceId}</if>",
            "</where>",
            "GROUP BY service_id, user_id",
            "ORDER BY service_id, user_id",
            "</script>"})
    @Results(id = "statsSummaryMap", value = {
        @Result(column = "service_id", property = "serviceId"),
        @Result(column = "user_id", property = "userId"),
        @Result(column = "total_calls", property = "totalCalls"),
        @Result(column = "success_calls", property = "successCalls"),
        @Result(column = "failed_calls", property = "failedCalls"),
        @Result(column = "avg_response_time_ms", property = "avgResponseTimeMs"),
        @Result(column = "max_response_time_ms", property = "maxResponseTimeMs")
    })
    List<StatsSummary> queryStats(@Param("userId") String userId,
                                   @Param("serviceId") String serviceId);
}