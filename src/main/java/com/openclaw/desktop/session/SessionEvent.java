package com.openclaw.desktop.session;

import java.time.Instant;
import java.util.List;

/**
 * 会话事件 — sealed 接口。
 */
public sealed interface SessionEvent {

    record Created(SessionKey key, Instant at)                    implements SessionEvent {}
    record MessageAdded(SessionKey key, String role, String content, Instant at) implements SessionEvent {}
    record Reset(SessionKey key, Instant at)                      implements SessionEvent {}
    record Deleted(SessionKey key, Instant at)                    implements SessionEvent {}
}
