package com.noinch.mcp.gateway.management.service;

import com.noinch.mcp.gateway.core.dto.MCPServiceCreateRequest;
import com.noinch.mcp.gateway.core.dto.MCPServiceUpdateRequest;
import com.noinch.mcp.gateway.core.entity.MCPServiceEntity;
import com.noinch.mcp.gateway.core.constant.ServiceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MCPService {
    Mono<MCPServiceEntity> createService(MCPServiceCreateRequest request);
    Mono<MCPServiceEntity> updateService(String serviceId, MCPServiceUpdateRequest request);
    Mono<Void> deleteService(String serviceId);
    Mono<MCPServiceEntity> getServiceByServiceId(String serviceId);
    Mono<Page<MCPServiceEntity>> getServices(ServiceStatus status, String name, Pageable pageable);
    Flux<MCPServiceEntity> getActiveServices();
    Mono<MCPServiceEntity> updateServiceStatus(String serviceId, ServiceStatus status);
    Mono<Boolean> performHealthCheck(String serviceId);
}