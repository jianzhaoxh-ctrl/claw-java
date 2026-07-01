package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.UsageInfo;

import java.util.List;

/**
 * 上下文组装结果 — ContextEngine.assemble() 的返回值。
 *
 * <p>包含筛选后的消息列表、估算的 token 数、
 * 系统提示注入内容、以及截断/压缩的原因说明。
 */
public record AssembleResult(
    List<Message> messages,
    int estimatedTokens,
    String systemPromptAddition,
    String promptAuthority,
    boolean wasTruncated,
    boolean wasCompacted,
    String truncationReason
) {
    /**
     * 简单成功结果。
     */
    public static AssembleResult of(List<Message> messages, int estimatedTokens) {
        return new AssembleResult(messages, estimatedTokens, "", "full", false, false, null);
    }

    /**
     * 截断结果。
     */
    public static AssembleResult truncated(List<Message> messages, int estimatedTokens, String reason) {
        return new AssembleResult(messages, estimatedTokens, "", "truncated", true, false, reason);
    }

    /**
     * 压缩结果。
     */
    public static AssembleResult compacted(List<Message> messages, int estimatedTokens, String systemPromptAddition) {
        return new AssembleResult(messages, estimatedTokens, systemPromptAddition, "compacted", false, true, "auto-compaction");
    }
}
