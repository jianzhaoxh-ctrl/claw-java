package com.openclaw.desktop.context;

/**
 * 上下文引擎配置。
 */
public record ContextEngineConfig(
    int maxTokens,
    int reservedTokens,
    String compactionModel,
    double compactionThreshold,
    boolean autoCompact,
    boolean includeSystemPrompt
) {
    public static ContextEngineConfig defaults() {
        return new ContextEngineConfig(
            128000,     // maxTokens：模型最大 token 数
            4096,       // reservedTokens：为回复预留的 token
            "gpt-4o",   // compactionModel：用于压缩摘要的模型
            0.8,        // compactionThreshold：达到 80% 时触发压缩
            true,       // autoCompact：自动压缩
            true        // includeSystemPrompt：包含系统提示
        );
    }
}
