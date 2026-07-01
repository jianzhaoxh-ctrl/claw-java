package com.openclaw.desktop.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本地 TTS — 使用系统 espeak/pico/festival 或 Edge TTS (免费)。
 * 对应 OpenClaw 的 tts-local-cli extension。
 */
public class LocalTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalTtsProvider.class);

    private final String engine;

    public LocalTtsProvider() {
        this(detectEngine());
    }

    public LocalTtsProvider(String engine) {
        this.engine = engine != null ? engine : "edge-tts";
    }

    @Override public String id() { return "local"; }
    @Override public String name() { return "本地 TTS (" + engine + ")"; }
    @Override public boolean isAvailable() {
        return switch (engine) {
            case "espeak" -> commandExists("espeak");
            case "pico" -> commandExists("pico2wave");
            case "edge-tts" -> commandExists("edge-tts") || commandExists("pip");
            default -> false;
        };
    }

    @Override
    public Mono<Path> synthesize(String text, String voice, Path outputPath) {
        return Mono.fromCallable(() -> {
            var pb = switch (engine) {
                case "espeak" -> new ProcessBuilder("espeak", "-w", outputPath.toString(), text);
                case "pico" -> new ProcessBuilder("pico2wave", "-w", outputPath.toString(), text);
                case "edge-tts" -> new ProcessBuilder("edge-tts",
                    "--voice", mapEdgeVoice(voice),
                    "--text", text,
                    "--write-media", outputPath.toString());
                default -> throw new UnsupportedOperationException("No local TTS engine available");
            };
            pb.redirectErrorStream(true);
            var process = pb.start();
            var finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new RuntimeException("TTS timed out");
            }
            if (process.exitValue() != 0) {
                throw new RuntimeException("TTS failed: exit " + process.exitValue());
            }
            log.info("Local TTS synthesized → {}", outputPath);
            return outputPath;
        });
    }

    @Override
    public List<String> voices() {
        return switch (engine) {
            case "edge-tts" -> List.of(
                "zh-CN-XiaoxiaoNeural (晓晓)",
                "zh-CN-YunxiNeural (云希)",
                "en-US-JennyNeural (Jenny)",
                "ja-JP-NanamiNeural (七海)"
            );
            case "espeak" -> List.of("default", "zh", "en", "ja");
            default -> List.of("default");
        };
    }

    private String mapEdgeVoice(String voice) {
        if (voice == null || voice.isBlank()) return "zh-CN-XiaoxiaoNeural";
        if (voice.contains("Xiaoxiao") || voice.equals("晓晓")) return "zh-CN-XiaoxiaoNeural";
        if (voice.contains("Yunxi") || voice.equals("云希")) return "zh-CN-YunxiNeural";
        if (voice.contains("Jenny")) return "en-US-JennyNeural";
        return voice;
    }

    private static String detectEngine() {
        if (commandExists("edge-tts")) return "edge-tts";
        if (commandExists("espeak")) return "espeak";
        if (commandExists("pico2wave")) return "pico";
        return "none";
    }

    private static boolean commandExists(String cmd) {
        try {
            var osName = System.getProperty("os.name").toLowerCase();
            var pb = osName.contains("win")
                ? new ProcessBuilder("cmd", "/c", "where " + cmd)
                : new ProcessBuilder("sh", "-c", "which " + cmd);
            pb.redirectErrorStream(true);
            var proc = pb.start();
            proc.waitFor(3, TimeUnit.SECONDS);
            return proc.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
