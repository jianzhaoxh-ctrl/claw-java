package com.openclaw.desktop.channel;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息通道接口 — 所有消息通道（Telegram, Discord 等）实现此接口。
 * 对应 OpenClaw 的 Channel。
 */
public interface Channel {

    /** 通道唯一标识 */
    ChannelId id();

    /** 通道配置 */
    ChannelConfig config();

    /** 启动通道 */
    Mono<Void> start();

    /** 停止通道 */
    Mono<Void> stop();

    /** 入站消息流 */
    Flux<InboundMessage> inbound();

    /** 发送消息 */
    Mono<Void> send(OutboundMessage message);

    /** 健康检查 */
    com.openclaw.desktop.infra.health.HealthCheckResult healthCheck();

    /** 是否运行中 */
    boolean isRunning();
}
