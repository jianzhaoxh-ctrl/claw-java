package com.openclaw.desktop.channel.wechat;

import com.openclaw.desktop.channel.*;
import com.openclaw.desktop.session.SessionKey;
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
 * 微信企业号通道 — 通过企业微信 API 接收/发送消息。
 * 对应 OpenClaw 的 tencent extension（微信）。
 *
 * 企业微信 API：
 * - 获取 access_token: GET https://qyapi.weixin.qq.com/cgi-bin/gettoken
 * - 接收消息回调（XML POST）
 * - 发送消息: POST https://qyapi.weixin.qq.com/cgi-bin/message/send
 */
public class WeChatWorkChannel implements Channel {

    private static final Logger log = LoggerFactory.getLogger(WeChatWorkChannel.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://qyapi.weixin.qq.com/cgi-bin";

    private final ChannelId id;
    private final WeChatWorkConfig wcConfig;
    private final ChannelConfig config;
    private final HttpClient httpClient;
    private final Sinks.Many<InboundMessage> messageSink;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile String accessToken;
    private volatile long tokenExpireAt;

    public WeChatWorkChannel(WeChatWorkConfig wcConfig) {
        this.id = new ChannelId("wechat");
        this.wcConfig = wcConfig;
        this.config = new ChannelConfig(id, wcConfig.enabled(), Map.of());
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
                log.info("Starting WeChat Work channel (corpId={})", wcConfig.corpId());
                ensureToken();
            }
        });
    }

    @Override
    public Mono<Void> stop() {
        return Mono.fromRunnable(() -> {
            running.set(false);
            log.info("WeChat Work channel stopped");
        });
    }

    @Override
    public Flux<InboundMessage> inbound() {
        return messageSink.asFlux();
    }

    /**
     * 接收企业微信回调消息（XML 格式，由 Gateway 转发为 JSON）。
     */
    public void handleCallback(String xml) {
        try {
            // 企业微信消息体是 XML，简化解析提取 <Content>
            var content = extractXmlElement(xml, "Content");
            var fromUser = extractXmlElement(xml, "FromUserName");
            if (!content.isEmpty()) {
                var inbound = InboundMessage.text(
                    id,
                    SessionKey.main("wechat-" + fromUser),
                    content
                );
                messageSink.tryEmitNext(inbound);
                log.debug("WeChat message from {}: {}", fromUser,
                    content.substring(0, Math.min(50, content.length())));
            }
        } catch (Exception e) {
            log.warn("Failed to handle WeChat callback: {}", e.getMessage());
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
            var userId = extractUserId(message);

            var body = MAPPER.createObjectNode();
            body.put("touser", userId);
            body.put("msgtype", "text");
            body.put("agentid", wcConfig.agentId());
            var textNode = MAPPER.createObjectNode();
            textNode.put("content", text);
            body.set("text", textNode);

            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/message/send?access_token=" + accessToken))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            var errcode = result.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new RuntimeException("WeChat send failed: " + result.path("errmsg").asText());
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

    private String extractUserId(OutboundMessage message) {
        var sk = message.sessionKey();
        return sk != null ? sk.toString().replace("main:wechat-", "") : "@all";
    }

    private void ensureToken() {
        if (accessToken != null && System.currentTimeMillis() < tokenExpireAt) return;
        try {
            var url = String.format("%s/gettoken?corpid=%s&corpsecret=%s",
                API_BASE, wcConfig.corpId(), wcConfig.corpSecret());
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            var result = MAPPER.readTree(response.body());
            var errcode = result.path("errcode").asInt(0);
            if (errcode != 0) {
                throw new RuntimeException("WeChat auth failed: " + result.path("errmsg").asText());
            }
            accessToken = result.path("access_token").asText();
            var expire = result.path("expires_in").asInt(7200);
            tokenExpireAt = System.currentTimeMillis() + (expire - 300) * 1000L;
            log.info("WeChat access_token refreshed, expires in {}s", expire);
        } catch (Exception e) {
            log.error("Failed to get WeChat access_token", e);
            throw new RuntimeException(e);
        }
    }

    private String extractXmlElement(String xml, String tag) {
        var pattern = java.util.regex.Pattern.compile("<" + tag + "><!\\[CDATA\\[(.*?)\\]\\]></" + tag + ">");
        var matcher = pattern.matcher(xml);
        return matcher.find() ? matcher.group(1) : "";
    }
}
