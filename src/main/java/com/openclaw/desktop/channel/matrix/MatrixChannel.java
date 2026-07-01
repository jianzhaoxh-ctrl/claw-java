package com.openclaw.desktop.channel.matrix;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Matrix 通道 — 通过 Matrix Client-Server API 接收/发送消息。
 * 对应 OpenClaw 的 matrix extension。
 * Matrix 是去中心化开源协议，支持自建服务器（如 Synapse/Dendrite）。
 */
public class MatrixChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(MatrixChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChannelId id;
    private final MatrixConfig mxConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final ScheduledExecutorService poller;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<String> sinceToken = new AtomicReference<>("");
    private volatile String accessToken;
    private volatile String userId;

    public MatrixChannel(MatrixConfig mxConfig) {
        this.id = new ChannelId("matrix");
        this.mxConfig = mxConfig;
        this.config = new ChannelConfig(id, mxConfig.enabled(), Map.of());
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.messageSink = Sinks.many().multicast().onBackpressureBuffer();
        this.poller = Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "matrix-poller");
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
                log.info("Starting Matrix channel: {}:{}", mxConfig.homeserver(), mxConfig.username());
                login();
                poller.scheduleWithFixedDelay(this::pollEvents, 0, 2, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            running.set(false);
            poller.shutdown();
            log.info("Matrix channel stopped");
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
            var roomId = extractRoomId(message);

            var body = MAPPER.createObjectNode();
            body.put("msgtype", "m.text");
            body.put("body", text);

            var txnId = "claw" + System.currentTimeMillis();
            var url = String.format("%s/_matrix/client/r0/rooms/%s/send/m.room.message/%s?access_token=%s",
                mxConfig.homeserver(), roomId, txnId, accessToken);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Matrix send failed: " + response.statusCode());
            }
            return null;
        });
    }

    @Override
    public com.openclaw.desktop.infra.health.HealthCheckResult healthCheck() {
        if (accessToken != null) {
            return com.openclaw.desktop.infra.health.HealthCheckResult.healthy();
        }
        return com.openclaw.desktop.infra.health.HealthCheckResult.unhealthy("Not logged in");
    }

    @Override
    public boolean isRunning() { return running.get(); }

    // ---- internal ----

    private String extractRoomId(OutboundMessage message) {
        var sk = message.sessionKey();
        return sk != null ? sk.toString().replace("main:matrix-", "") : mxConfig.defaultRoomId();
    }

    private void login() {
        try {
            var body = MAPPER.createObjectNode();
            body.put("type", "m.login.password");
            var identifier = MAPPER.createObjectNode();
            identifier.put("type", "m.id.user");
            identifier.put("user", mxConfig.username());
            body.set("identifier", identifier);
            body.put("password", mxConfig.password());

            var request = HttpRequest.newBuilder()
                .uri(URI.create(mxConfig.homeserver() + "/_matrix/client/r0/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            accessToken = result.path("access_token").asText("");
            userId = result.path("user_id").asText("");

            if (accessToken.isEmpty()) {
                throw new RuntimeException("Matrix login failed: no access_token");
            }
            log.info("Matrix logged in as {}", userId);
        } catch (Exception e) {
            log.error("Matrix login failed", e);
            throw new RuntimeException(e);
        }
    }

    private void pollEvents() {
        try {
            var since = sinceToken.get();
            var url = String.format("%s/_matrix/client/r0/sync?timeout=30000&access_token=%s",
                mxConfig.homeserver(), accessToken);
            if (!since.isEmpty()) {
                url += "&since=" + since;
            }

            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(35))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            var newSince = result.path("next_batch").asText("");
            if (!newSince.isEmpty()) {
                sinceToken.set(newSince);
            }

            var rooms = result.path("rooms").path("join");
            rooms.fields().forEachRemaining(entry -> {
                var roomId = entry.getKey();
                var roomData = entry.getValue();
                var timeline = roomData.path("timeline").path("events");
                for (var event : timeline) {
                    var type = event.path("type").asText("");
                    if (!"m.room.message".equals(type)) continue;
                    var sender = event.path("sender").asText("");
                    if (sender.equals(userId)) continue; // 跳过自己的消息

                    var content = event.path("content");
                    var msgtype = content.path("msgtype").asText("");
                    var body = content.path("body").asText("");

                    if ("m.text".equals(msgtype) && !body.isEmpty()) {
                        var inbound = InboundMessage.text(
                            id,
                            SessionKey.main("matrix-" + roomId),
                            body
                        );
                        messageSink.tryEmitNext(inbound);
                        log.debug("Matrix message from {} in {}: {}", sender, roomId,
                            body.substring(0, Math.min(50, body.length())));
                    }
                }
            });
        } catch (Exception e) {
            log.warn("Matrix poll failed: {}", e.getMessage());
        }
    }
}
