package com.openclaw.desktop.tts;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * ElevenLabs TTS — 高质量 AI 语音合成。
 * 对应 OpenClaw 的 elevenlabs extension。
 * 需要 ElevenLabs API Key。
 */
public class ElevenLabsTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(ElevenLabsTtsProvider.class);
    private static final String API_BASE = "https://api.elevenlabs.io/v1";

    private final String apiKey;
    private final HttpClient httpClient;

    public ElevenLabsTtsProvider(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override public String id() { return "elevenlabs"; }
    @Override public String name() { return "ElevenLabs"; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Mono<Path> synthesize(String text, String voice, Path outputPath) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("ElevenLabs API key not configured"));
        }
        var voiceId = mapVoice(voice);
        return Mono.fromCallable(() -> {
            var url = API_BASE + "/text-to-speech/" + voiceId;
            var body = String.format(
                "{\"text\":\"%s\",\"model_id\":\"eleven_multilingual_v2\",\"voice_settings\":{\"stability\":0.5,\"similarity_boost\":0.8}}",
                text.replace("\"", "\\\"").replace("\n", "\\n")
            );
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("xi-api-key", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "audio/mpeg")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(60))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("ElevenLabs error: " + response.statusCode());
            }
            Files.write(outputPath, response.body());
            log.info("TTS synthesized: {} bytes → {}", response.body().length, outputPath);
            return outputPath;
        });
    }

    @Override
    public List<String> voices() {
        return List.of("rachel", "domi", "bella", "antoni", "elli", "josh", "arnold", "adam", "sam",
            "nova (女声-温暖)", "onyx (男声-深沉)", "shimmer (女声-明亮)");
    }

    private String mapVoice(String voice) {
        if (voice == null || voice.isBlank()) return "21m00Tcm4TlvDq8ikWAM";
        // ElevenLabs 用 voice ID，简化为名称映射
        return switch (voice.toLowerCase()) {
            case "rachel", "nova" -> "21m00Tcm4TlvDq8ikWAM";
            case "domi" -> "AZnzlk1XvdvUeBnXmlld";
            case "bella", "shimmer" -> "EXAVITQu4vr4xnSDxMAC";
            case "antoni" -> "ErXwobaYiN019PkySvjV";
            case "josh", "onyx" -> "TxGEqnHWrfWFTfGW9XjX";
            case "arnold" -> "VR6AewLTigWG4xSOukaG";
            case "adam" -> "pNInz6obpgDQGcFmaJgB";
            case "sam" -> "yoZ06aMxZJJ28mfd3POQ";
            default -> "21m00Tcm4TlvDq8ikWAM"; // 默认 Rachel
        };
    }
}
