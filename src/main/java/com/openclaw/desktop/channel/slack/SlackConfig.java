package com.openclaw.desktop.channel.slack;

/**
 * Slack 通道配置。
 */
public record SlackConfig(
    String botToken,
    String appToken,
    boolean enabled
) {
    public static SlackConfig of(String botToken) {
        return new SlackConfig(botToken, null, true);
    }

    public static SlackConfig defaults() {
        return new SlackConfig("", "", false);
    }
}
