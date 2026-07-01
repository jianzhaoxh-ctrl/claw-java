package com.openclaw.desktop.llm;

/**
 * Token 使用量信息。
 */
public record UsageInfo(
    int promptTokens,
    int completionTokens,
    int totalTokens,
    Double inputCost,
    Double outputCost
) {}
