package com.openclaw.desktop.channel;

import java.util.Map;

/**
 * 通道配置。
 */
public record ChannelConfig(
    ChannelId id,
    boolean enabled,
    Map<String, Object> settings
) {}
