package com.openclaw.desktop;

import com.openclaw.desktop.config.ClawConfig;
import com.openclaw.desktop.config.ConfigLoader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigLoader 测试。
 */
class ConfigLoaderTest {

    @Test
    void testDefaults() {
        var config = ClawConfig.defaults();
        assertEquals(7180, config.gateway().port());
        assertEquals("gpt-4o", config.agent().modelId());
        assertEquals("127.0.0.1", config.gateway().bindAddress());
    }

    @Test
    void testLoadFromHocon() {
        var config = ConfigLoader.load();
        assertNotNull(config);
        // 默认值
        assertEquals(7180, config.gateway().port());
    }
}
