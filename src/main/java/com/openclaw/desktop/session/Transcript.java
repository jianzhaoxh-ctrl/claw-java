package com.openclaw.desktop.session;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话记录 — 存储会话中的所有消息。
 */
public class Transcript {

    private final List<TranscriptEntry> entries = new ArrayList<>();

    public void add(TranscriptEntry entry) {
        entries.add(entry);
    }

    public void clear() {
        entries.clear();
    }

    public List<TranscriptEntry> entries() {
        return List.copyOf(entries);
    }

    public int size() {
        return entries.size();
    }

    public TranscriptEntry last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }
}
