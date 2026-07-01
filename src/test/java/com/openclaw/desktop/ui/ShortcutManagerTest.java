package com.openclaw.desktop.ui;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShortcutManagerTest {

    private ShortcutManager manager;

    @BeforeEach
    void setUp() {
        manager = new ShortcutManager();
    }

    @Test
    void testDefaultBindingsCount() {
        var bindings = manager.allBindings();
        assertEquals(8, bindings.size());
    }

    @Test
    void testHelpText() {
        var help = manager.helpText();
        assertNotNull(help);
        assertTrue(help.contains("Ctrl+N"));
        assertTrue(help.contains("Ctrl+S"));
        assertTrue(help.contains("Ctrl+/"));
    }

    @Test
    void testOverrideBinding() {
        var triggered = new boolean[]{false};
        manager.override("save", () -> { triggered[0] = true; });
        // 验证覆盖不改变绑定数量
        assertEquals(8, manager.allBindings().size());
    }

    @Test
    void testRegisterNewBinding() {
        manager.register("test-shortcut",
            new KeyCodeCombination(KeyCode.T, KeyCombination.SHORTCUT_DOWN),
            () -> {});
        assertEquals(9, manager.allBindings().size());
    }
}
