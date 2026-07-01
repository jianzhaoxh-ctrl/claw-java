package com.openclaw.desktop.ui.chat;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 打字机效果渲染器 — 将流式接收的文本以打字机动画逐字呈现。
 *
 * <p>工作原理：
 * <ul>
 *   <li>维护一个「目标文本缓冲区」（已接收的全部文本）</li>
 *   <li>维护一个「已渲染字符数」指针</li>
 *   <li>每帧（约 16ms）从指针位置追加若干字符到目标节点</li>
 *   <li>当追上目标缓冲区时暂停，等待下次 {@link #appendDelta}</li>
 * </ul>
 *
 * <p>当接收速率远快于渲染速率时，自动加速（每帧追加更多字符）以避免积压。
 * 流结束时调用 {@link #finish} 强制立即渲染剩余内容。
 */
public class TypewriterRenderer {

    private static final Logger log = LoggerFactory.getLogger(TypewriterRenderer.class);
    private static final double FRAME_MS = 16;          // 约 60fps
    private static final int BASE_CHARS_PER_FRAME = 3;   // 基础每帧字符数
    private static final int MAX_CHARS_PER_FRAME = 40;   // 最大每帧字符数（加速上限）

    private final StringBuilder targetBuffer = new StringBuilder();
    private final AtomicReference<String> renderedText = new AtomicReference<>("");
    private int renderedChars = 0;
    private final Consumer<String> renderCallback;
    private final VBox messageBubble;
    private Timeline timeline;
    private boolean finished = false;

    public TypewriterRenderer(VBox messageBubble, Consumer<String> renderCallback) {
        this.messageBubble = messageBubble;
        this.renderCallback = renderCallback;
    }

    /**
     * 追加流式增量文本。
     */
    public void appendDelta(String delta) {
        if (delta == null || delta.isEmpty()) return;
        synchronized (targetBuffer) {
            targetBuffer.append(delta);
        }
        startTimelineIfNeeded();
    }

    /**
     * 标记流结束，立即渲染全部剩余文本。
     */
    public void finish() {
        finished = true;
        stopTimeline();
        Platform.runLater(() -> {
            renderTo(targetBuffer.toString());
            renderedChars = targetBuffer.length();
        });
    }

    /**
     * 重置（用于消息复用）。
     */
    public void reset() {
        synchronized (targetBuffer) {
            targetBuffer.setLength(0);
        }
        renderedChars = 0;
        renderedText.set("");
        finished = false;
        stopTimeline();
    }

    /**
     * 当前已渲染的文本。
     */
    public String renderedText() {
        return renderedText.get();
    }

    /**
     * 目标文本（已接收但可能未全部渲染）。
     */
    public String targetText() {
        synchronized (targetBuffer) {
            return targetBuffer.toString();
        }
    }

    /**
     * 是否还有未渲染的内容。
     */
    public boolean hasPending() {
        synchronized (targetBuffer) {
            return renderedChars < targetBuffer.length();
        }
    }

    private void startTimelineIfNeeded() {
        if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) return;
        Platform.runLater(() -> {
            if (timeline != null && timeline.getStatus() == Animation.Status.RUNNING) return;
            timeline = new Timeline(new KeyFrame(Duration.millis(FRAME_MS), e -> tick()));
            timeline.setCycleCount(Animation.INDEFINITE);
            timeline.play();
        });
    }

    private void stopTimeline() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    private void tick() {
        int targetLen;
        synchronized (targetBuffer) {
            targetLen = targetBuffer.length();
        }
        if (renderedChars >= targetLen) {
            // 已追上，暂停时间轴
            stopTimeline();
            return;
        }

        // 根据积压量动态调整每帧字符数
        int pending = targetLen - renderedChars;
        int charsThisFrame = Math.min(MAX_CHARS_PER_FRAME,
            Math.max(BASE_CHARS_PER_FRAME, pending / 20));

        int newLen = Math.min(targetLen, renderedChars + charsThisFrame);
        String toRender;
        synchronized (targetBuffer) {
            toRender = targetBuffer.substring(0, newLen);
        }
        renderTo(toRender);
        renderedChars = newLen;
    }

    private void renderTo(String text) {
        renderedText.set(text);
        try {
            renderCallback.accept(text);
        } catch (Exception e) {
            log.error("Render callback failed: {}", e.getMessage());
        }
    }
}
