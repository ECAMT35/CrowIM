package com.ecamt35.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayWebSocketConfig {

    /**
     * WebSocket 路由配置
     */
    @Bean
    public RouteLocator websocketRoute(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("netty-websocket-im", r -> r
                                .path("/ws/**")
                                .filters(f -> f
//                                .rewritePath("/ws/(?<segment>.*)", "/${segment}")
                                )
                                // Netty IM WebSocket 服务地址，单机或 lb:ws://im-service 多实例
                                .uri("ws://localhost:8081")
                )
                .build();
    }

//    /**
//     * WebSocket 全局过滤器
//     * 用于 token 校验和透传用户信息
//     */
//    @Bean
//    public GlobalFilter wsAuthFilter() {
//        return new GlobalFilter() {
//
//            @Override
//            public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
//                String path = exchange.getRequest().getURI().getPath();
//                if (!path.startsWith("/ws")) {
//                    return chain.filter(exchange); // 非 WebSocket 请求直接放行
//                }
//
//                // 获取 token，可从 query 或 header
//                String token = exchange.getRequest().getQueryParams().getFirst("token");
//                if (token == null || token.isBlank()) {
//                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
//                    return exchange.getResponse().setComplete();
//                }
//
//                String userId = parseUserId(token);
//
//                // 透传用户 ID 到 Netty IM
//                ServerWebExchange mutated = exchange.mutate()
//                        .request(exchange.getRequest().mutate()
//                                .header("X-User-Id", userId)
//                                .build())
//                        .build();
//
//                return chain.filter(mutated);
//            }
//
//            private String parseUserId(String token) {
//                // 模拟解析 token 获取用户 ID
//                return "10001";
//            }
//        };
//    }
}