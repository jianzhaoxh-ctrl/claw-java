package com.openclaw.desktop.memory;

import java.time.Instant;

/**
 * 记忆条目。
 */
public record MemoryEntry(
    String id,
    String content,
    String sessionKey,
    String[] tags,
    Instant createdAt,
    Instant updatedAt
) {}
