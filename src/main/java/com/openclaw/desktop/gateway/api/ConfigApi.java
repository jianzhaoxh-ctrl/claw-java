package com.openclaw.desktop.gateway.api;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.gateway.server.GatewayRouter;
import com.fasterxml.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * 配置 API — GET/PATCH /v1/config
 */
public class ConfigApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ClawConfig config;

    public ConfigApi(ClawConfig config) {
        this.config = config;
    }

    public Mono<Void> getConfig(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        return Mono.fromCallable(() -> MAPPER.writeValueAsString(config))
            .flatMap(json -> res.header("Content-Type", "application/json").sendString(Mono.just(json)).then());
    }

    public Mono<Void> updateConfig(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        return req.receive().aggregate().asString().flatMap(body ->
            res.header("Content-Type", "application/json")
                .sendString(Mono.just("{\"status\":\"updated\"}")).then()
        );
    }
}
