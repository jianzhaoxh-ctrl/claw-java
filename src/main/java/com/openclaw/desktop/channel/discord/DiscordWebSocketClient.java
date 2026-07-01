package com.openclaw.desktop.channel.discord;

import com.openclaw.desktop.channel.ChannelId;
import com.openclaw.desktop.channel.InboundMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Discord Gateway WebSocket 客户端�? * 处理 IDENTIFY、HEARTBEAT、MESSAGE_CREATE 等事件�? */
public class DiscordWebSocketClient implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(DiscordWebSocketClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String gatewayUrl;
    private final String botToken;
    private final String applicationId;
    private final Sinks.Many<InboundMessage> messageSink;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private volatile boolean connected = false;
    private volatile String sessionId;
    private volatile int heartbeatInterval;
    private volatile int lastSeq;
    private final StringBuilder messageBuffer = new StringBuilder();

    public DiscordWebSocketClient(String gatewayUrl, String botToken, String applicationId,
                                   Sinks.Many<InboundMessage> messageSink) {
        this.gatewayUrl = gatewayUrl;
        this.botToken = botToken;
        this.applicationId = applicationId;
        this.messageSink = messageSink;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void connect() {
        log.info("Connecting to Discord Gateway: {}", gatewayUrl);
        webSocket = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(gatewayUrl + "/?v=10&encoding=json"), this)
            .join();
        try {
            connectLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect() {
        log.info("Disconnecting from Discord Gateway");
        connected = false;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("Discord WebSocket opened");
        connected = true;
        WebSocket.Listener.super.onOpen(webSocket);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageBuffer.append(data);
        if (last) {
            var message = messageBuffer.toString();
            messageBuffer.setLength(0);
            handleMessage(message);
        }
        return WebSocket.Listener.super.onText(webSocket, data, last);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        log.error("Discord WebSocket error: {}", error.getMessage());
        connected = false;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("Discord WebSocket closed: {} - {}", statusCode, reason);
        connected = false;
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    private void handleMessage(String json) {
        try {
            var payload = MAPPER.readTree(json);
            var op = payload.path("op").asInt();
            var seq = payload.path("s");
            if (!seq.isMissingNode() && !seq.isNull()) {
                lastSeq = seq.asInt();
            }

            switch (op) {
                case 10 -> { // HELLO
                    heartbeatInterval = payload.path("d").path("heartbeat_interval").asInt();
                    log.info("Discord HELLO: heartbeat_interval={}ms", heartbeatInterval);
                    sendIdentify();
                    startHeartbeat();
                    connectLatch.countDown();
                }
                case 11 -> { // HEARTBEAT ACK
                    log.debug("Discord heartbeat ACK");
                }
                case 0 -> { // DISPATCH
                    var t = payload.path("t").asText();
                    var d = payload.path("d");
                    handleDispatch(t, d);
                }
                case 7 -> { // RECONNECT
                    log.info("Discord requested reconnect");
                    disconnect();
                    connect();
                }
                case 9 -> { // INVALID SESSION
                    log.warn("Discord invalid session");
                    disconnect();
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle Discord message: {}", e.getMessage());
        }
    }

    private void sendIdentify() {
        try {
            var identify = MAPPER.createObjectNode();
            identify.put("op", 2);
            var data = MAPPER.createObjectNode();
            data.put("token", botToken);
            var props = MAPPER.createObjectNode();
            props.put("os", System.getProperty("os.name").toLowerCase());
            props.put("browser", "ClawDesktop");
            props.put("device", "ClawDesktop");
            data.set("properties", props);
            data.put("intents", 512 | 32768); // GUILD_MESSAGES | MESSAGE_CONTENT
            identify.set("d", data);
            webSocket.sendText(MAPPER.writeValueAsString(identify), true);
            log.info("Discord IDENTIFY sent");
        } catch (Exception e) {
            log.error("Failed to send Discord IDENTIFY: {}", e.getMessage());
        }
    }

    private void startHeartbeat() {
        var scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "discord-heartbeat");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            try {
                var heartbeat = MAPPER.createObjectNode();
                heartbeat.put("op", 1);
                heartbeat.put("d", lastSeq);
                webSocket.sendText(MAPPER.writeValueAsString(heartbeat), true);
            } catch (Exception e) {
                log.warn("Failed to send Discord heartbeat: {}", e.getMessage());
            }
        }, heartbeatInterval / 2, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    private void handleDispatch(String type, JsonNode data) {
        switch (type) {
            case "READY" -> {
                sessionId = data.path("session_id").asText();
                var user = data.path("user");
                log.info("Discord READY: {}#{}",
                    user.path("username").asText(),
                    user.path("discriminator").asText());
            }
            case "MESSAGE_CREATE" -> {
                var content = data.path("content").asText("");
                var author = data.path("author");
                var username = author.path("username").asText("");
                var userId = author.path("id").asText("");
                var channelId = data.path("channel_id").asText("");
                var messageId = data.path("id").asText("");

                // 忽略 bot 自己的消�?                if (author.path("bot").asBoolean(false)) return;

                if (!content.isEmpty()) {
                    var inbound = InboundMessage.text(
                        new ChannelId("discord"),
                        com.openclaw.desktop.session.SessionKey.main("discord-" + channelId),
                        content
                    );
                    messageSink.tryEmitNext(inbound);
                    log.debug("Discord message from {} in {}: {}",
                        username, channelId,
                        content.substring(0, Math.min(50, content.length())));
                }
            }
        }
    }
}
