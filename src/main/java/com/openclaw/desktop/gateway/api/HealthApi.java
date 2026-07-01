package com.openclaw.desktop.gateway.api;

import com.openclaw.desktop.infra.health.HealthCheckResult;
import com.openclaw.desktop.gateway.server.GatewayRouter;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * 健康检查 API — GET /health
 */
public class HealthApi {

    public Mono<Void> health(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var result = HealthCheckResult.healthy();
        var body = "{\"status\":\"" + result.status() + "\",\"message\":\"" + result.message() + "\"}";
        return res.header("Content-Type", "application/json").sendString(Mono.just(body)).then();
    }
}
