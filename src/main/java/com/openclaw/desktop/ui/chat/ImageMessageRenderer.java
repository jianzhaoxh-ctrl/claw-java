package com.openclaw.desktop.ui.chat;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图片消息渲染器 — 支持 Base64 内嵌图片与 URL 图片的显示。
 *
 * <p>识别两种格式：
 * <ul>
 *   <li>{@code ![alt](data:image/png;base64,XXXX)} — Base64 内嵌</li>
 *   <li>{@code ![alt](https://example.com/img.png)} — URL 引用</li>
 * </ul>
 *
 * <p>URL 图片在后台线程加载，加载完成后更新 ImageView。
 */
public class ImageMessageRenderer {

    private static final Logger log = LoggerFactory.getLogger(ImageMessageRenderer.class);
    private static final Pattern IMAGE_PATTERN = Pattern.compile(
        "!\\[(.*?)]\\((data:image/[\\w+]+;base64,[^)]+|https?://[^)]+\\.(?:png|jpg|jpeg|gif|webp|svg))\\)",
        Pattern.CASE_INSENSITIVE);

    /** 最大图片显示宽度。 */
    private static final double MAX_WIDTH = 400;
    /** 最大图片显示高度。 */
    private static final double MAX_HEIGHT = 300;

    /**
     * 从文本中提取并渲染所有图片，返回包含图片与剩余文本的容器。
     * @param text 原始文本（可能含图片 Markdown 语法）
     * @return 渲染容器，若无图片则返回 null
     */
    public VBox renderImages(String text) {
        if (text == null || text.isEmpty()) return null;
        var matcher = IMAGE_PATTERN.matcher(text);
        if (!matcher.find()) return null;

        var container = new VBox(8);
        container.setPadding(new Insets(4));
        matcher.reset();

        var lastEnd = 0;
        while (matcher.find()) {
            // matcher 前的文本
            if (matcher.start() > lastEnd) {
                var before = text.substring(lastEnd, matcher.start());
                if (!before.isBlank()) {
                    var label = new Label(before.trim());
                    label.setWrapText(true);
                    container.getChildren().add(label);
                }
            }
            var alt = matcher.group(1);
            var src = matcher.group(2);
            var imageView = createImageView(src, alt);
            if (imageView != null) {
                container.getChildren().add(imageView);
            } else {
                var errLabel = new Label("[图片加载失败: " + alt + "]");
                errLabel.setStyle("-fx-text-fill: #f38ba8; -fx-font-style: italic;");
                container.getChildren().add(errLabel);
            }
            lastEnd = matcher.end();
        }
        // 末尾文本
        if (lastEnd < text.length()) {
            var after = text.substring(lastEnd);
            if (!after.isBlank()) {
                var label = new Label(after.trim());
                label.setWrapText(true);
                container.getChildren().add(label);
            }
        }
        return container;
    }

    /**
     * 检查文本是否包含图片。
     */
    public boolean hasImages(String text) {
        return text != null && IMAGE_PATTERN.matcher(text).find();
    }

    private javafx.scene.Node createImageView(String src, String alt) {
        try {
            if (src.startsWith("data:image/")) {
                return createFromBase64(src, alt);
            } else if (src.startsWith("http://") || src.startsWith("https://")) {
                return createFromUrl(src, alt);
            }
        } catch (Exception e) {
            log.warn("Image load failed: {} - {}", src.substring(0, Math.min(50, src.length())), e.getMessage());
        }
        return null;
    }

    private javafx.scene.Node createFromBase64(String dataUrl, String alt) {
        var commaIdx = dataUrl.indexOf(',');
        if (commaIdx < 0) return null;
        var base64 = dataUrl.substring(commaIdx + 1);
        try {
            var bytes = Base64.getDecoder().decode(base64);
            var image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) return null;
            var view = new ImageView(image);
            view.setPreserveRatio(true);
            view.setFitWidth(Math.min(image.getWidth(), MAX_WIDTH));
            if (image.getHeight() > MAX_HEIGHT) {
                view.setFitHeight(MAX_HEIGHT);
            }
            javafx.scene.control.Tooltip.install(view, new javafx.scene.control.Tooltip(alt));
            return view;
        } catch (Exception e) {
            log.warn("Base64 image decode failed: {}", e.getMessage());
            return null;
        }
    }

    private javafx.scene.Node createFromUrl(String url, String alt) {
        var box = new VBox(4);
        box.setAlignment(Pos.CENTER);
        var placeholder = new ProgressIndicator();
        placeholder.setPrefSize(30, 30);
        var altLabel = new Label(alt);
        altLabel.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 11;");
        box.getChildren().addAll(placeholder, altLabel);

        // 后台线程加载
        Thread.ofVirtual().name("img-loader").start(() -> {
            try {
                var image = new Image(url, true);
                image.progressProperty().addListener((obs, o, n) -> {
                    if (n.doubleValue() >= 1.0 && !image.isError()) {
                        Platform.runLater(() -> {
                            box.getChildren().clear();
                            var view = new ImageView(image);
                            view.setPreserveRatio(true);
                            view.setFitWidth(Math.min(image.getWidth(), MAX_WIDTH));
                            if (image.getHeight() > MAX_HEIGHT) {
                                view.setFitHeight(MAX_HEIGHT);
                            }
                            javafx.scene.control.Tooltip.install(view, new javafx.scene.control.Tooltip(alt));
                            box.getChildren().add(view);
                        });
                    }
                });
                image.errorProperty().addListener((obs, o, n) -> {
                    if (n) Platform.runLater(() -> {
                        box.getChildren().clear();
                        box.getChildren().add(new Label("[图片加载失败]"));
                    });
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    box.getChildren().clear();
                    box.getChildren().add(new Label("[图片加载失败: " + e.getMessage() + "]"));
                });
            }
        });
        return box;
    }
}
