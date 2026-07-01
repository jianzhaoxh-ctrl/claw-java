package com.openclaw.desktop.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 图标生成器测试。
 */
class IconGeneratorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("generate produces non-empty image of given size")
    void testGenerate() {
        var img = IconGenerator.generate(256);
        assertNotNull(img);
        assertEquals(256, img.getWidth());
        assertEquals(256, img.getHeight());
        // 确保不是完全透明
        var pixel = img.getRGB(128, 128);
        var alpha = (pixel >> 24) & 0xFF;
        assertTrue(alpha > 0, "center pixel should be non-transparent");
    }

    @Test
    @DisplayName("generate handles multiple sizes")
    void testMultipleSizes() {
        for (var size : new int[]{16, 32, 64, 128, 256}) {
            var img = IconGenerator.generate(size);
            assertEquals(size, img.getWidth());
            assertEquals(size, img.getHeight());
        }
    }

    @Test
    @DisplayName("savePng writes a valid PNG file")
    void testSavePng() throws Exception {
        var out = tempDir.resolve("test-icon.png");
        IconGenerator.savePng(out, 128);
        assertTrue(java.nio.file.Files.exists(out));
        var read = ImageIO.read(out.toFile());
        assertNotNull(read);
        assertEquals(128, read.getWidth());
    }

    @Test
    @DisplayName("saveMultiSize creates all requested sizes")
    void testSaveMultiSize() throws Exception {
        var outDir = tempDir.resolve("icons");
        var sizes = new int[]{16, 32, 64};
        IconGenerator.saveMultiSize(outDir, sizes);
        for (var s : sizes) {
            var file = outDir.resolve("icon-" + s + ".png");
            assertTrue(java.nio.file.Files.exists(file), "missing icon-" + s + ".png");
        }
    }
}
