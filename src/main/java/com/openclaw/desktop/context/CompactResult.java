package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;

import java.util.List;

/**
 * 上下文压缩结果 — ContextEngine.compact() 的返回值。
 */
public record CompactResult(
    List<Message> compactedMessages,
    Message summaryMessage,
    int originalTokens,
    int compactedTokens,
    double compressionRatio
) {
    /**
     * 压缩成功。
     */
    public static CompactResult success(List<Message> compactedMessages, Message summaryMessage,
                                         int originalTokens, int compactedTokens) {
        return new CompactResult(compactedMessages, summaryMessage, originalTokens, compactedTokens,
            (double) compactedTokens / originalTokens);
    }

    /**
     * 无需压缩（token 数未超阈值）。
     */
    public static CompactResult noNeed(List<Message> originalMessages, int tokenCount) {
        return new CompactResult(originalMessages, null, tokenCount, tokenCount, 1.0);
    }
}
