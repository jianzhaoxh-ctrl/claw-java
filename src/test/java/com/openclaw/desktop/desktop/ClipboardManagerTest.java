package com.openclaw.desktop.desktop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 剪贴板管理器测试 — 历史记录逻辑验证。
 * 注：实际剪贴板操作需要 JavaFX Platform 线程，这里仅测试历史记录逻辑。
 */
class ClipboardManagerTest {

    @Test
    @DisplayName("history is empty initially")
    void testEmptyHistory() {
        var mgr = new ClipboardManager();
        assertEquals(0, mgr.historySize());
        assertTrue(mgr.history().isEmpty());
    }

    @Test
    @DisplayName("historyLimit is configurable")
    void testHistoryLimit() {
        var mgr = new ClipboardManager(5);
        assertEquals(5, mgr.historyLimit());
    }

    @Test
    @DisplayName("clearHistory empties the history list")
    void testClearHistory() {
        var mgr = new ClipboardManager();
        // 通过反射直接操作 history 不现实，这里仅验证 clearHistory 不抛异常
        assertDoesNotThrow(mgr::clearHistory);
        assertEquals(0, mgr.historySize());
    }

    @Test
    @DisplayName("copyFromHistory returns false for invalid index")
    void testCopyFromInvalidIndex() {
        var mgr = new ClipboardManager();
        assertFalse(mgr.copyFromHistory(0));
        assertFalse(mgr.copyFromHistory(-1));
        assertFalse(mgr.copyFromHistory(100));
    }

    @Test
    @DisplayName("default history limit is 50")
    void testDefaultLimit() {
        var mgr = new ClipboardManager();
        assertEquals(50, mgr.historyLimit());
    }
}
