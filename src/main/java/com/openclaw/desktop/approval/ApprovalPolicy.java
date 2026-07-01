package com.openclaw.desktop.approval;

/**
 * 审批策略 — 定义哪些操作需要用户确认。
 * 对应 OpenClaw 的 tools.exec.ask / host approvals 机制。
 */
public enum ApprovalPolicy {
    /** 所有操作自动批准，无需用户确认 */
    AUTO_APPROVE,
    /** 危险操作（删除、发送邮件等）需要确认 */
    CONFIRM_DANGEROUS,
    /** 所有修改操作都需要确认 */
    CONFIRM_MUTATIONS,
    /** 所有操作都需要确认（最严格） */
    CONFIRM_ALL
}
