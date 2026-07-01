package com.openclaw.desktop.approval;

/**
 * 审批结果 — 用户对审批请求的响应。
 */
public record ApprovalResult(
    String requestId,
    ApprovalDecision decision,
    String reason,
    boolean allowOnce,
    long approvedAt
) {
    public enum ApprovalDecision {
        APPROVED,
        DENIED,
        DEFERRED  // 延迟决策，稍后处理
    }

    public static ApprovalResult approved(String requestId) {
        return new ApprovalResult(requestId, ApprovalDecision.APPROVED, null, false, System.currentTimeMillis());
    }

    public static ApprovalResult approvedOnce(String requestId) {
        return new ApprovalResult(requestId, ApprovalDecision.APPROVED, null, true, System.currentTimeMillis());
    }

    public static ApprovalResult denied(String requestId, String reason) {
        return new ApprovalResult(requestId, ApprovalDecision.DENIED, reason, false, System.currentTimeMillis());
    }

    public static ApprovalResult deferred(String requestId) {
        return new ApprovalResult(requestId, ApprovalDecision.DEFERRED, null, false, System.currentTimeMillis());
    }
}
