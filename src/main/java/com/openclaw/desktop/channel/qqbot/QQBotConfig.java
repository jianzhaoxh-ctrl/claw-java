package com.openclaw.desktop.channel.qqbot;

/**
 * QQ Bot 配置。
 */
public record QQBotConfig(
    String appId,
    String appSecret,
    String botToken,   // Bot 的 WebSocket 鉴权 Token
    boolean enabled
) {
    public static QQBotConfig defaults() {
        return new QQBotConfig("", "", "", false);
    }
}
