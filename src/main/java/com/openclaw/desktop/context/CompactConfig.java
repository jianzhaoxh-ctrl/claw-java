package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;

import java.util.List;

/**
 * 压缩配置。
 */
public record CompactConfig(
    String strategy,
    int targetTokens,
    boolean keepSystemPrompt,
    boolean keepLastNMessages,
    int keepLastN,
    String summaryPrompt
) {
    public static CompactConfig defaults() {
        return new CompactConfig(
            "sliding-window",   // strategy: sliding-window / summary / hybrid
            8000,               // targetTokens: 压缩后目标 token 数
            true,               // keepSystemPrompt
            true,               // keepLastNMessages: 保留最近 N 条消息不压缩
            4,                  // keepLastN: 保留最近 4 条消息
            "请将以下对话历史压缩为简洁摘要，保留关键信息和决策，丢弃无关细节。"  // summaryPrompt
        );
    }
}
