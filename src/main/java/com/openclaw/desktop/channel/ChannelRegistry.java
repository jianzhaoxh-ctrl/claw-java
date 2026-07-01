package com.openclaw.desktop.channel;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 通道注册表 — 管理所有已注册的消息通道。
 */
public class ChannelRegistry {

    private final ConcurrentMap<String, Channel> channels = new ConcurrentHashMap<>();

    public void register(Channel channel) {
        channels.put(channel.id().value(), channel);
    }

    public void unregister(ChannelId id) {
        channels.remove(id.value());
    }

    public Optional<Channel> get(ChannelId id) {
        return Optional.ofNullable(channels.get(id.value()));
    }

    public java.util.Collection<Channel> all() {
        return channels.values();
    }

    public int count() {
        return channels.size();
    }
}
