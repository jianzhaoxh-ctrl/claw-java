package com.openclaw.desktop.channel;

import com.openclaw.desktop.session.SessionKey;

import java.util.Map;

/**
 * 出站消息 — 发送到消息通道的消息。
 */
public record OutboundMessage(
    String id,
    SessionKey sessionKey,
    OutboundContent content,
    SendOptions options
) {
    public sealed interface OutboundContent {
        record Text(String text) implements OutboundContent {}
        record Image(String url) implements OutboundContent {}
        record Audio(String url) implements OutboundContent {}
        record Video(String url) implements OutboundContent {}
        record File(String name, String mimeType, byte[] data) implements OutboundContent {}
    }

    public record SendOptions(
        boolean silent,
        String replyToMessageId,
        Map<String, Object> extras
    ) {
        public static SendOptions defaults() {
            return new SendOptions(false, null, Map.of());
        }
    }

    public static OutboundMessage text(SessionKey sessionKey, String text) {
        return new OutboundMessage(
            java.util.UUID.randomUUID().toString(),
            sessionKey,
            new OutboundContent.Text(text),
            SendOptions.defaults()
        );
    }
}
