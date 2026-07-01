package com.openclaw.desktop.session;

import java.time.Instant;

/**
 * 对话记录条目。
 */
public record TranscriptEntry(
    String id,
    String role,       // system, user, assistant, tool
    String content,
    String toolCallId,
    Instant timestamp
) {}
