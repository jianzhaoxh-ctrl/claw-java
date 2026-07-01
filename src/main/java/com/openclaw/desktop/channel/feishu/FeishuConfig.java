package com.openclaw.desktop.channel.feishu;

/**
 * 飞书配置。
 */
public record FeishuConfig(
    String appId,
    String appSecret,
    String verificationToken,  // 事件订阅验证令牌
    String encryptKey,         // 事件订阅加密密钥（可选）
    boolean enabled
) {
    public static FeishuConfig defaults() {
        return new FeishuConfig("", "", "", "", false);
    }
}
