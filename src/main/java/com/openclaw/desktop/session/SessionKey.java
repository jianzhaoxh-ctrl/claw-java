package com.openclaw.desktop.session;

/**
 * 会话键 — 唯一标识一个会话。
 * 对应 OpenClaw 的 SessionKey。
 */
public record SessionKey(
    SessionKind kind,
    String agentId,
    String channelId,
    String userId,
    String threadId
) {
    public enum SessionKind { MAIN, CHILD, BOOT, SUBAGENT }

    public static SessionKey main(String agentId) {
        return new SessionKey(SessionKind.MAIN, agentId, null, null, null);
    }

    public static SessionKey child(String agentId, String channelId, String userId, String threadId) {
        return new SessionKey(SessionKind.CHILD, agentId, channelId, userId, threadId);
    }

    public static SessionKey subagent(String agentId, String subAgentId) {
        return new SessionKey(SessionKind.SUBAGENT, agentId, null, null, subAgentId);
    }

    @Override
    public String toString() {
        return switch (kind) {
            case MAIN -> "main:" + agentId;
            case CHILD -> "child:" + agentId + ":" + channelId + ":" + userId + ":" + threadId;
            case BOOT -> "boot:" + agentId;
            case SUBAGENT -> "subagent:" + agentId + ":" + threadId;
        };
    }
}
