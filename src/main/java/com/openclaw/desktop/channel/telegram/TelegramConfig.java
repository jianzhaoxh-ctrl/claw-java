package com.openclaw.desktop.channel.telegram;

/**
 * Telegram 通道配置。
 */
public record TelegramConfig(
    String botToken,
    long pollIntervalMs,
    boolean enabled
) {
    public static TelegramConfig of(String botToken) {
        return new TelegramConfig(botToken, 1000, true);
    }

    public static TelegramConfig defaults() {
        return new TelegramConfig("", 1000, false);
    }
}
