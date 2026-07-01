package com.openclaw.desktop.channel.mail;

/**
 * 邮件通道配置。
 */
public record MailConfig(
    String imapHost,
    int imapPort,
    String smtpHost,
    int smtpPort,
    String username,
    String password,
    int pollIntervalSec,  // IMAP 轮询间隔（秒）
    boolean enabled
) {
    public static MailConfig defaults() {
        return new MailConfig("", 993, "", 587, "", "", 60, false);
    }
}
