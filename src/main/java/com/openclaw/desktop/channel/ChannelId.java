package com.openclaw.desktop.channel;

/**
 * 通道标识符。
 */
public record ChannelId(String value) {
    public static final ChannelId WEB      = new ChannelId("web");
    public static final ChannelId TELEGRAM  = new ChannelId("telegram");
    public static final ChannelId DISCORD   = new ChannelId("discord");
    public static final ChannelId SLACK     = new ChannelId("slack");
    public static final ChannelId WHATSAPP  = new ChannelId("whatsapp");
    public static final ChannelId SIGNAL    = new ChannelId("signal");
    public static final ChannelId IMESSAGE  = new ChannelId("imessage");
    public static final ChannelId MATRIX    = new ChannelId("matrix");
    public static final ChannelId FEISHU    = new ChannelId("feishu");
    public static final ChannelId WECHAT    = new ChannelId("wechat");
    public static final ChannelId QQBOT     = new ChannelId("qqbot");
    public static final ChannelId MAIL      = new ChannelId("mail");
    public static final ChannelId WEBHOOK   = new ChannelId("webhook");
}
