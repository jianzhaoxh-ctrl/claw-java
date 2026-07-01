package com.openclaw.desktop.tts;

import reactor.core.publisher.Mono;

import java.nio.file.Path;

/**
 * TTS 接口 — 文本转语音。
 * 对应 OpenClaw 的 speech-core / tts-local-cli / elevenlabs / azure-speech。
 */
public interface TtsProvider {

    /** Provider ID */
    String id();

    /** Provider 名称 */
    String name();

    /**
     * 将文本合成为语音文件。
     * @param text 要合成的文本
     * @param voice 语音名称（如 "nova", "onyx", "zh-CN-XiaoxiaoNeural"）
     * @param outputPath 输出文件路径（.mp3 或 .wav）
     * @return 完成信号
     */
    Mono<Path> synthesize(String text, String voice, Path outputPath);

    /** 是否可用 */
    boolean isAvailable();

    /** 支持的语音列表 */
    java.util.List<String> voices();
}
