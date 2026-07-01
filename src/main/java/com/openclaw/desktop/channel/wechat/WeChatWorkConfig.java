package com.openclaw.desktop.channel.wechat;

/**
 * 企业微信配置。
 */
public record WeChatWorkConfig(
    String corpId,
    String corpSecret,
    String agentId,    // 应用 AgentId
    String token,      // 回调 Token
    String encodingAesKey, // 回调加密密钥
    boolean enabled
) {
    public static WeChatWorkConfig defaults() {
        return new WeChatWorkConfig("", "", "", "", "", false);
    }
}
