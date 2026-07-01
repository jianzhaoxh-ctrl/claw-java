package com.openclaw.desktop.media;

import reactor.core.publisher.Mono;

import java.nio.file.Path;
import java.util.List;

/**
 * 视觉理解接口 — 使用多模态 LLM（GPT-4o/Claude 3.5/Gemini）分析图片。
 * 对应 OpenClaw 的 media-understanding。
 */
public interface VisionProvider {

    String id();
    String name();

    /**
     * 分析图片。
     * @param imagePath 图片路径（本地或 URL）
     * @param prompt 分析指令（如"描述这张图片"、"提取文字"）
     * @return 分析结果文本
     */
    Mono<String> analyzeImage(String imagePath, String prompt);

    /**
     * 批量分析图片。
     */
    Mono<List<String>> analyzeImages(List<String> imagePaths, String prompt);

    /** 是否可用 */
    boolean isAvailable();
}
