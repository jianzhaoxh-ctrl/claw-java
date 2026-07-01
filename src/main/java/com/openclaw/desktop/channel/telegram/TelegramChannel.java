package com.openclaw.desktop.channel.telegram;

import com.openclaw.desktop.channel.*;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Telegram Bot 通道 — 通过 Telegram Bot API 接收和发送消息。
 * 对应 OpenClaw 的 telegram extension。
 */
public class TelegramChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(TelegramChannel.class);
    private static final String API_BASE = "https://api.telegram.org/bot";

    private final ChannelId id;
    private final TelegramConfig tgConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final ScheduledExecutorService poller;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong lastUpdateId = new AtomicLong(0);

    public TelegramChannel(TelegramConfig config) {
        this.id = new ChannelId("telegram");
        this.tgConfig = config;
        this.config = new com.openclaw.desktop.channel.ChannelConfig(id, config.enabled(), java.util.Map.of());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "telegram-poller");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public ChannelId id() { return id; }

    @Override
    public ChannelConfig config() { return config; }

    @Override
    public Mono<Void> start() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(false, true)) {
                log.info("Starting Telegram channel: {}", id);
                // 验证 Bot Token
                var me = callApi("getMe").block();
                if (me == null || !me.contains("\"ok\":true")) {
                    throw new RuntimeException("Invalid Telegram bot token");
                }
                log.info("Telegram bot verified: {}", me);
                // 开始轮询更�?                poller.scheduleWithFixedDelay(this::pollUpdates, 0, config.pollIntervalMs(), TimeUnit.MILLISECONDS);
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            if (running.compareAndSet(true, false)) {
                log.info("Stopping Telegram channel: {}", id);
                poller.shutdown();
            }
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    @Override
    public Mono<Void> send(OutboundMessage message) {
        return Mono.fromCallable(() -> {
            var text = switch (message.content()) {
                case OutboundMessage.OutboundContent.Text(var t) -> t;
                default -> message.content().toString();
            };
            var chatId = message.sessionKey() != null ? message.sessionKey().toString() : "";
            var body = String.format(
                "{\"chat_id\":\"%s\",\"text\":\"%s\",\"parse_mode\":\"Markdown\"}",
                chatId,
                escapeJson(text)
            );
            return callApi("sendMessage", body).block();
        }).then();
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        try {
            var response = callApi("getMe").block();
            var healthy = response != null && response.contains("\"ok\":true");
            return healthy
                ? com.openclaw.desktop.infra.health.HealthCheckResult.healthy()
                : com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy("Bot token invalid");
        } catch (Exception e) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy(e.getMessage());
        }
    }

    @Override
    public boolean isRunning() { return running.get(); }

    // ---- internal ----

    private void pollUpdates() {
        try {
            var body = String.format(
                "{\"offset\":%d,\"timeout\":30,\"allowed_updates\":[\"message\",\"edited_message\"]}",
                lastUpdateId.get() + 1
            );
            var response = callApi("getUpdates", body).block();
            if (response != null) {
                parseUpdates(response);
            }
        } catch (Exception e) {
            log.warn("Failed to poll Telegram updates: {}", e.getMessage());
        }
    }

    private void parseUpdates(String json) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(json);
            if (!root.path("ok").asBoolean()) return;

            var result = root.path("result");
            if (!result.isArray()) return;

            for (var update : result) {
                var updateId = update.path("update_id").asLong();
                lastUpdateId.set(updateId);

                var message = update.path("message");
                if (message.isMissingNode()) {
                    message = update.path("edited_message");
                }
                if (message.isMissingNode()) continue;

                var chat = message.path("chat");
                var chatId = chat.path("id").asText();
                var text = message.path("text").asText("");
                var from = message.path("from");
                var userId = from.path("id").asText();
                var username = from.path("username").asText("");

                if (!text.isEmpty()) {
                    var inbound = InboundMessage.text(
                        id(),
                        com.openclaw.desktop.session.SessionKey.main("telegram-" + chatId),
                        text
                    );
                    messageSink.tryEmitNext(inbound);
                    log.debug("Received Telegram message from {}: {}", username, text.substring(0, Math.min(50, text.length())));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse Telegram updates: {}", e.getMessage());
        }
    }

    private Mono<String> callApi(String method) {
        return callApi(method, null);
    }

    private Mono<String> callApi(String method, String body) {
        return Mono.fromCallable(() -> {
            var url = API_BASE + tgConfig.botToken() + "/" + method;
            var builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30));

            if (body != null) {
                builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            } else {
                builder.GET();
            }

            var response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return response.body();
        });
    }

    private String escapeJson(String text) {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
