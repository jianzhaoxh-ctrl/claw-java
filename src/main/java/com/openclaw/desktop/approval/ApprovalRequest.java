package com.openclaw.desktop.approval;

import java.time.Instant;

/**
 * 审批请求 — 需要用户确认的操作。
 * 对应 OpenClaw 的 elevated / approval 机制。
 */
public record ApprovalRequest(
    String id,
    String toolName,
    String operation,
    String description,
    ApprovalLevel level,
    String commandPreview,
    Instant createdAt,
    String sessionId
) {
    public enum ApprovalLevel {
        /** 低风险操作，默认自动批准 */
        LOW,
        /** 需要用户一次性确认 */
        NORMAL,
        /** 高风险操作，每次都必须确认 */
        HIGH,
        /** 非常危险，需要明确批准且记录 */
        CRITICAL
    }

    public static ApprovalRequest of(String toolName, String operation, String description, ApprovalLevel level) {
        return new ApprovalRequest(
            java.util.UUID.randomUUID().toString(),
            toolName, operation, description, level,
            "", Instant.now(), "default"
        );
    }

    public static ApprovalRequest of(String toolName, String operation, String description, ApprovalLevel level, String commandPreview) {
        return new ApprovalRequest(
            java.util.UUID.randomUUID().toString(),
            toolName, operation, description, level,
            commandPreview, Instant.now(), "default"
        );
    }
}
