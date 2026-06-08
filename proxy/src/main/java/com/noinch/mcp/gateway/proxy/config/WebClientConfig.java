package com.noinch.mcp.gateway.proxy.config;

import com.noinch.mcp.gateway.core.config.ProxyConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient webClient(ProxyConfig proxyConfig) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(proxyConfig.getTimeout())
                .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) proxyConfig.getConnectTimeout().toMillis());

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize((int) proxyConfig.getMaxInMemorySize())
                )
                .filter((request, next) -> next
                        .exchange(request)
                        .retryWhen(Retry.backoff(proxyConfig.getMaxRetries(), proxyConfig.getRetryBackoff())
                                .filter(throwable -> !(throwable instanceof IllegalArgumentException)))
                )
                .build();
    }
}
