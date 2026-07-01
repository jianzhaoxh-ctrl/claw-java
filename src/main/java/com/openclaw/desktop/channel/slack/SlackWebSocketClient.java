package com.openclaw.desktop.channel.slack;

import com.openclaw.desktop.channel.ChannelId;
import com.openclaw.desktop.channel.InboundMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Sinks;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Slack RTM WebSocket 客户端。
 * 使用 Socket Mode (WSS) 接收实时消息。
 */
public class SlackWebSocketClient implements WebSocket.Listener {

    private static final Logger log = LoggerFactory.getLogger(SlackWebSocketClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String wssUrl;
    private final String botToken;
    private final Sinks.Many<InboundMessage> messageSink;
    private final HttpClient httpClient;
    private WebSocket webSocket;
    private final CountDownLatch connectLatch = new CountDownLatch(1);
    private final StringBuilder messageBuffer = new StringBuilder();

    public SlackWebSocketClient(String wssUrl, String botToken, Sinks.Many<InboundMessage> messageSink) {
        this.wssUrl = wssUrl;
        this.botToken = botToken;
        this.messageSink = messageSink;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public void connect() {
        log.info("Connecting to Slack RTM: {}", wssUrl);
        webSocket = httpClient.newWebSocketBuilder()
            .buildAsync(URI.create(wssUrl), this)
            .join();
        try {
            connectLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void disconnect() {
        log.info("Disconnecting from Slack RTM");
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye");
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        log.info("Slack WebSocket opened");
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
        log.error("Slack WebSocket error: {}", error.getMessage());
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        log.info("Slack WebSocket closed: {} - {}", statusCode, reason);
        return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
    }

    private void handleMessage(String json) {
        try {
            var payload = MAPPER.readTree(json);
            var type = payload.path("type").asText("");

            switch (type) {
                case "hello" -> {
                    log.info("Slack RTM connected");
                    connectLatch.countDown();
                }
                case "message" -> {
                    var text = payload.path("text").asText("");
                    var channel = payload.path("channel").asText("");
                    var subtype = payload.path("subtype").asText("");

                    // 忽略 bot 消息
                    if (!subtype.equals("bot_message") && !text.isEmpty()) {
                        var inbound = InboundMessage.text(
                            new ChannelId("slack"),
                            com.openclaw.desktop.session.SessionKey.main("slack-" + channel),
                            text
                        );
                        messageSink.tryEmitNext(inbound);
                        log.debug("Slack message in {}: {}", channel,
                            text.substring(0, Math.min(50, text.length())));
                    }
                }
                case "reconnect_url" -> {
                    log.info("Slack RTM reconnect URL received");
                }
                case "ping" -> {
                    try {
                        var pong = MAPPER.createObjectNode();
                        pong.put("type", "pong");
                        pong.put("reply_to", payload.path("reply_to").asInt());
                        webSocket.sendText(MAPPER.writeValueAsString(pong), true);
                    } catch (Exception e) {
                        log.warn("Failed to send Slack pong");
                    }
                }
                default -> { /* ignore */ }
            }
        } catch (Exception e) {
            log.error("Failed to handle Slack message: {}", e.getMessage());
        }
    }
}
