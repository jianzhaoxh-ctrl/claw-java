package com.openclaw.desktop.llm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 模型元数据。
 */
public record Model(
    String id,
    String name,
    String providerId,
    int contextWindow,
    int maxOutputTokens,
    boolean supportsStreaming,
    boolean supportsToolCalling,
    boolean supportsVision,
    boolean supportsThinking,
    Map<String, Object> metadata,
    Instant addedAt
) {
    public static Model of(String id, String name, String providerId) {
        return new Model(id, name, providerId, 0, 0, true, false, false, false, Map.of(), Instant.now());
    }
}
