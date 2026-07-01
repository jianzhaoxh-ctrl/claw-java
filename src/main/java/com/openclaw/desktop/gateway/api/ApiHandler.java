package com.openclaw.desktop.gateway.api;

import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * API 处理器接口 — 所有 API 端点实现此函数式接口。
 */
@FunctionalInterface
public interface ApiHandler {
    reactor.core.publisher.Mono<Void> handle(HttpServerRequest req, HttpServerResponse res);
}
