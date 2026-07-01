package com.openclaw.desktop.channel.webhook;

/**
 * Webhook 配置。
 */
public record WebhookConfig(
    String inboundToken,   // 入站验证 token（URL 路径中的 token）
    String outboundUrl,    // 出站 Webhook URL（回复消息 POST 到此 URL）
    boolean enabled
) {
    public static WebhookConfig defaults() {
        return new WebhookConfig("", "", false);
    }
}
