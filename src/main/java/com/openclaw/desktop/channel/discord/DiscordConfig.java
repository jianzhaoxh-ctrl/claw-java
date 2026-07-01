package com.openclaw.desktop.channel.discord;

/**
 * Discord 通道配置。
 */
public record DiscordConfig(
    String botToken,
    String applicationId,
    String defaultGuildId,
    boolean enabled
) {

    public static DiscordConfig of(String botToken, String applicationId) {
        return new DiscordConfig(botToken, applicationId, null, true);
    }

    public static DiscordConfig defaults() {
        return new DiscordConfig("", "", null, false);
    }
}
