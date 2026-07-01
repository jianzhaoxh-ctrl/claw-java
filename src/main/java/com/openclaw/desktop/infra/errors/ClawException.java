package com.openclaw.desktop.infra.errors;

/**
 * 根异常 — 所有 ClawDesktop 业务异常的父类（sealed，不可任意扩展）。
 */
public sealed class ClawException extends RuntimeException {

    public static final class AgentErr extends ClawException {
        public AgentErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class LlmErr extends ClawException {
        public LlmErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class ChannelErr extends ClawException {
        public ChannelErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class ConfigErr extends ClawException {
        public ConfigErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class ToolErr extends ClawException {
        public ToolErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class AuthErr extends ClawException {
        public AuthErr(String message, Throwable cause) { super(message, cause); }
    }
    public static final class MemoryErr extends ClawException {
        public MemoryErr(String message, Throwable cause) { super(message, cause); }
    }

    protected ClawException(String message, Throwable cause) {
        super(message, cause);
    }
}
