package com.openclaw.desktop.channel.feishu;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 飞书机器人通道 — 通过飞书开放平台 Bot Webhook 接收/发送消息。
 * 对应 OpenClaw 的 feishu extension。
 *
 * 飞书 Bot 使用：
 * - 事件回调（用户消息推送到本地 HTTP 端点）
 * - 发消息 API（POST https://open.feishu.cn/open-apis/im/v1/messages）
 * - 需要 App ID + App Secret 换取 tenant_access_token
 */
public class FeishuChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(FeishuChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://open.feishu.cn/open-apis";

    private final ChannelId id;
    private final FeishuConfig feishuConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String tenantAccessToken;
    private volatile long tokenExpireAt;

    public FeishuChannel(FeishuConfig feishuConfig) {
        this.id = new ChannelId("feishu");
        this.feishuConfig = feishuConfig;
        this.config = new ChannelConfig(id, feishuConfig.enabled(), Map.of());
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
                log.info("Starting Feishu channel (appId={})", feishuConfig.appId());
                refreshTenantToken();
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            running.set(false);
            log.info("Feishu channel stopped");
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    /**
     * 接收来自飞书事件订阅的回调（由 Gateway HTTP 端点转发）。
     */
    public void handleEventCallback(String json) {
        try {
            var payload = MAPPER.readTree(json);

            // 飞书 URL 验证 challenge
            if (payload.has("challenge")) {
                return; // challenge 响应由 Gateway 层处理
            }

            var header = payload.path("header");
            var eventType = header.path("event_type").asText("");

            if ("im.message.receive_v1".equals(eventType)) {
                var event = payload.path("event");
                var message = event.path("message");
                var chatId = message.path("chat_id").asText("");
                var msgType = message.path("message_type").asText("text");
                var contentStr = message.path("content").asText("{}");

                String text = "";
                try {
                    var content = MAPPER.readTree(contentStr);
                    text = content.path("text").asText("");
                } catch (Exception ignored) {}

                if (!text.isEmpty()) {
                    var sender = event.path("sender").path("sender_id").path("open_id").asText("unknown");
                    var inbound = InboundMessage.text(
                        id,
                        SessionKey.main("feishu-" + chatId),
                        text
                    );
                    messageSink.tryEmitNext(inbound);
                    log.debug("Feishu message from {} in {}: {}", sender, chatId,
                        text.substring(0, Math.min(50, text.length())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to handle Feishu event: {}", e.getMessage());
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
            var receiveId = extractReceiveId(message);

            var body = MAPPER.createObjectNode();
            body.put("receive_id", receiveId);
            body.put("msg_type", "text");
            var contentNode = MAPPER.createObjectNode();
            contentNode.put("text", text);
            body.put("content", MAPPER.writeValueAsString(contentNode));

            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/im/v1/messages?receive_id_type=chat_id"))
                .header("Authorization", "Bearer " + tenantAccessToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Feishu send failed: " + response.statusCode() + " " + response.body());
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

    private String extractReceiveId(OutboundMessage message) {
        var sk = message.sessionKey();
        return sk != null ? sk.toString().replace("main:feishu-", "") : "";
    }

    private void ensureToken() {
        if (tenantAccessToken == null || System.currentTimeMillis() > tokenExpireAt) {
            refreshTenantToken();
        }
    }

    private void refreshTenantToken() {
        try {
            var body = MAPPER.createObjectNode();
            body.put("app_id", feishuConfig.appId());
            body.put("app_secret", feishuConfig.appSecret());

            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/auth/v3/tenant_access_token/internal"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            var code = result.path("code").asInt(-1);
            if (code != 0) {
                throw new RuntimeException("Feishu auth failed: " + result.path("msg").asText());
            }
            tenantAccessToken = result.path("tenant_access_token").asText();
            var expire = result.path("expire").asInt(7200);
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("Feishu tenant_access_token refreshed, expires in {}s", expire);
        } catch (Exception e) {
            log.error("Failed to refresh Feishu token", e);
            throw new RuntimeException(e);
        }
    }
}
