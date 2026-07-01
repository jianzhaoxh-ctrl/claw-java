package com.openclaw.desktop.gateway.server;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.infra.logging.ClawLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.time.Duration;

/**
 * 网关服务器 — 基于 Reactor Netty 的 HTTP + WebSocket 服务器。
 * 对应 OpenClaw 的 GatewayServer。
 */
public class GatewayServer {

    private static final Logger log = LoggerFactory.getLogger(GatewayServer.class);

    private final ClawConfig config;
    private final GatewayRouter router;
    private DisposableServer server;
    private volatile ServerState state = ServerState.STOPPED;

    public GatewayServer(ClawConfig config) {
        this.config = config;
        this.router = new GatewayRouter(config);
    }

    public GatewayServer(ClawConfig config, com.openclaw.desktop.agent.Agent agent, com.openclaw.desktop.tool.ToolRegistry toolRegistry) {
        this.config = config;
        this.router = new GatewayRouter(config, agent, toolRegistry);
    }

    public Mono<Void> start() {
        if (state == ServerState.RUNNING) {
            log.warn("GatewayServer already running");
            return Mono.empty();
        }
        state = ServerState.STARTING;
        log.info("GatewayServer starting on {}:{}...", config.gateway().bindAddress(), config.gateway().port());

        return HttpServer.create()
            .host(config.gateway().bindAddress())
            .port(config.gateway().port())
            .route(router::configure)
            .bind()
            .doOnSuccess(s -> {
                this.server = s;
                this.state = ServerState.RUNNING;
                log.info("GatewayServer started on port {}", s.port());
            })
            .doOnError(e -> {
                this.state = ServerState.FAILED;
                log.error("GatewayServer failed to start", e);
            })
            .timeout(Duration.ofSeconds(15))
            .then();
    }

    public Mono<Void> stop() {
        if (state != ServerState.RUNNING) return Mono.empty();
        state = ServerState.STOPPING;
        log.info("GatewayServer stopping...");
        if (server != null) {
            return Mono.<Void>fromRunnable(() -> server.disposeNow())
                .doOnSuccess(_v -> {
                    state = ServerState.STOPPED;
                    log.info("GatewayServer stopped");
                });
        }
        state = ServerState.STOPPED;
        return Mono.empty();
    }

    public Mono<Void> restart() {
        return stop().then(start());
    }

    public ServerState state() { return state; }
    public int port() { return server != null ? server.port() : -1; }

    public enum ServerState { STOPPED, STARTING, RUNNING, STOPPING, RESTARTING, FAILED }
}
