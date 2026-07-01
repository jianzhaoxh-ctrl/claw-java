package com.openclaw.desktop;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 配置系统测试。
 */
class ConfigSystemTest {

    @Test
    @DisplayName("Default config should have correct values")
    void testDefaultConfig() {
        var config = ClawConfig.defaults();

        assertNotNull(config);
        assertEquals(7180, config.gateway().port());
        assertEquals("127.0.0.1", config.gateway().bindAddress());
        assertEquals("default", config.agent().id());
        assertEquals("gpt-4o", config.agent().modelId());
        assertNotNull(config.memory().dbPath());
    }

    @Test
    @DisplayName("ConfigLoader should load defaults when no file exists")
    void testConfigLoaderDefaults() {
        var config = ConfigLoader.load();
        assertNotNull(config);
        assertNotNull(config.gateway());
        assertNotNull(config.agent());
        assertNotNull(config.llm());
    }
}
