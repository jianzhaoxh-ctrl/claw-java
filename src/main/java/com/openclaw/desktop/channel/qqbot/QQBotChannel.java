package com.openclaw.desktop.channel.qqbot;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * QQ 机器人通道 — 通过腾讯 QQ Bot 开放平台 API 接收/发送消息。
 * 对应 OpenClaw 的 qqbot extension。
 *
 * QQ Bot 开放平台：
 * - WebSocket 推送事件（C2C/群消息）
 * - 发送消息: POST https://api.sgroup.qq.com/v2/...
 * - 需要 AppID + Token 鉴权
 */
public class QQBotChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(QQBotChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://api.sgroup.qq.com/v2";

    private final ChannelId id;
    private final QQBotConfig qqConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String appAccessToken;
    private volatile long tokenExpireAt;

    public QQBotChannel(QQBotConfig qqConfig) {
        this.id = new ChannelId("qqbot");
        this.qqConfig = qqConfig;
        this.config = new ChannelConfig(id, qqConfig.enabled(), Map.of());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
    }

    @Override
    public ChannelId id() { return id; }

    @Override
    public ChannelConfig config() { return config; }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("Starting QQ Bot channel (appId={})", qqConfig.appId());
                refreshAccessToken();
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            running.set(false);
            log.info("QQ Bot channel stopped");
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    /**
     * 接收来自 QQ Bot WebSocket 推送的事件（由 Gateway WebSocket 层转发）。
     */
    public void handleEvent(String json) {
        try {
            var payload = MAPPER.readTree(json);
            var eventType = payload.path("t").asText("");

            if ("C2C_MESSAGE_CREATE".equals(eventType) || "GROUP_AT_MESSAGE_CREATE".equals(eventType)) {
                var data = payload.path("d");
                var content = data.path("content").asText("").trim();
                var authorId = data.path("author").path("id").asText("unknown");
                var groupId = data.path("group_openid").asText("");

                if (!content.isEmpty()) {
                    var sessionKey = groupId.isEmpty()
                        ? SessionKey.main("qq-" + authorId)
                        : SessionKey.main("qq-group-" + groupId);
                    var inbound = InboundMessage.text(id, sessionKey, content);
                    messageSink.tryEmitNext(inbound);
                    log.debug("QQ Bot message from {}: {}", authorId,
                        content.substring(0, Math.min(50, content.length())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to handle QQ Bot event: {}", e.getMessage());
        }
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        return Mono.fromCallable(() -> {
            ensureToken();
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };

            var sk = message.sessionKey();
            var target = sk != null ? sk.toString() : "";

            var body = MAPPER.createObjectNode();
            body.put("content", text);
            body.put("msg_type", 0);

            // 判断是 C2C 还是群消息
            String url;
            if (target.startsWith("main:qq-group-")) {
                var groupId = target.replace("main:qq-group-", "");
                body.put("group_openid", groupId);
                url = API_BASE + "/groups/" + groupId + "/messages";
            } else {
                var userId = target.replace("main:qq-", "");
                body.put("openid", userId);
                url = API_BASE + "/users/" + userId + "/messages";
            }

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "QQBot " + appAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new RuntimeException("QQ Bot send failed: " + response.statusCode() + " " + response.body());
            }
            return null;
        });
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        try {
            ensureToken();
            return com.openclaw.desktop.infra.health.HealthCheckResult.healthy();
        } catch (Exception e) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy(e.getMessage());
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    // ---- internal ----

    private void ensureToken() {
        if (appAccessToken != null && System.currentTimeMillis() < tokenExpireAt) return;
        refreshAccessToken();
    }

    private void refreshAccessToken() {
        try {
            var body = MAPPER.createObjectNode();
            body.put("grant_type", "client_credentials");
            body.put("appid", qqConfig.appId());
            body.put("secret", qqConfig.appSecret());

            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://bots.qq.com/app/getAppAccessToken"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            appAccessToken = result.path("access_token").asText("");
            var expire = result.path("expires_in").asInt(7200);
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("QQ Bot access_token refreshed, expires in {}s", expire);
        } catch (Exception e) {
            log.error("Failed to get QQ Bot access_token", e);
            throw new RuntimeException(e);
        }
    }
}
