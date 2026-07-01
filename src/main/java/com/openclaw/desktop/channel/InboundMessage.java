package com.openclaw.desktop.channel;

import com.openclaw.desktop.session.SessionKey;

import java.time.Instant;
import java.util.Map;

/**
 * 入站消息 — 从消息通道接收到的消息。
 */
public record InboundMessage(
    String id,
    ChannelId channelId,
    SessionKey sessionKey,
    String content,
    MessageType type,
    Map<String, Object> metadata,
    Instant timestamp
) {
    public enum MessageType { TEXT, IMAGE, AUDIO, VIDEO, FILE, STICKER, LOCATION, CONTACT }

    public static InboundMessage text(ChannelId channelId, SessionKey sessionKey, String content) {
        return new InboundMessage(
            java.util.UUID.randomUUID().toString(),
            channelId, sessionKey, content, MessageType.TEXT,
            Map.of(), Instant.now()
        );
    }
}
