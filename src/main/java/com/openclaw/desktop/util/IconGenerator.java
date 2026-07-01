package com.openclaw.desktop.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 应用图标生成器 — 生成 ClawDesktop 应用图标（PNG）。
 *
 * <p>用于 jpackage 打包、系统托盘、窗口图标。
 * 避免在源码仓库中存储二进制图片资源，运行时生成。
 */
public final class IconGenerator {

    private IconGenerator() {}

    /**
     * 生成 256x256 应用图标。
     */
    public static BufferedImage generate(int size) {
        var img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 圆角蓝色背景
        var arc = size / 6;
        g.setColor(new Color(79, 129, 189));
        g.fillRoundRect(0, 0, size, size, arc, arc);

        // 渐变叠加
        var grad = new GradientPaint(0, 0, new Color(137, 180, 250, 200),
            0, size, new Color(79, 129, 189, 255));
        g.setPaint(grad);
        g.fillRoundRect(0, 0, size, size, arc, arc);

        // 中央 "C" 字母
        g.setColor(Color.WHITE);
        var fontSize = (int) (size * 0.6);
        g.setFont(new Font("Arial", Font.BOLD, fontSize));
        var fm = g.getFontMetrics();
        var text = "C";
        var textWidth = fm.stringWidth(text);
        var textX = (size - textWidth) / 2;
        var textY = (size - fm.getHeight()) / 2 + fm.getAscent();
        g.drawString(text, textX, textY);

        g.dispose();
        return img;
    }

    /**
     * 保存图标到文件（PNG）。
     */
    public static void savePng(Path outputPath, int size) throws IOException {
        var img = generate(size);
        javax.imageio.ImageIO.write(img, "png", outputPath.toFile());
    }

    /**
     * 生成多尺寸图标并保存（16/32/64/128/256）。
     */
    public static void saveMultiSize(Path outputDir, int[] sizes) throws IOException {
        java.nio.file.Files.createDirectories(outputDir);
        for (var s : sizes) {
            savePng(outputDir.resolve("icon-" + s + ".png"), s);
        }
    }

    /**
     * 主入口 — 生成图标到指定目录。
     * 用法：java -cp claw-java.jar com.openclaw.desktop.util.IconGenerator <outputDir>
     */
    public static void main(String[] args) throws Exception {
        var outDir = args.length > 0
            ? Path.of(args[0])
            : Path.of("packaging", "icons");
        var sizes = new int[]{16, 32, 64, 128, 256};
        saveMultiSize(outDir, sizes);
        System.out.println("Icons generated to: " + outDir.toAbsolutePath());
    }
}
