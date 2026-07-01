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
 * Azure Speech TTS — 微软认知服务语音合成。
 * 对应 OpenClaw 的 azure-speech extension。
 * 支持中文普通话（晓晓/云扬/云希等）。
 */
public class AzureSpeechTtsProvider implements TtsProvider {

    private static final Logger log = LoggerFactory.getLogger(AzureSpeechTtsProvider.class);

    private final String apiKey;
    private final String region;
    private final HttpClient httpClient;

    public AzureSpeechTtsProvider(String apiKey, String region) {
        this.apiKey = apiKey;
        this.region = region != null ? region : "eastasia";
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override public String id() { return "azure-speech"; }
    @Override public String name() { return "Azure Speech"; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    @Override
    public Mono<Path> synthesize(String text, String voice, Path outputPath) {
        if (!isAvailable()) {
            return Mono.error(new IllegalStateException("Azure Speech key not configured"));
        }
        var voiceName = mapVoice(voice);
        return Mono.fromCallable(() -> {
            var ssml = String.format(
                "<speak version='1.0' xml:lang='zh-CN'>" +
                "<voice name='%s'>%s</voice></speak>",
                voiceName, escapeXml(text)
            );

            var url = String.format("https://%s.tts.speech.microsoft.com/cognitiveservices/v1", region);
            var request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .header("Content-Type", "application/ssml+xml")
                .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                .header("User-Agent", "ClawDesktop")
                .POST(HttpRequest.BodyPublishers.ofString(ssml))
                .timeout(Duration.ofSeconds(30))
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Azure TTS error: " + response.statusCode());
            }
            Files.write(outputPath, response.body());
            log.info("Azure TTS synthesized: {} bytes → {}", response.body().length, outputPath);
            return outputPath;
        });
    }

    @Override
    public List<String> voices() {
        return List.of(
            "zh-CN-XiaoxiaoNeural (晓晓-女声)",
            "zh-CN-YunyangNeural (云扬-男声)",
            "zh-CN-YunxiNeural (云希-男声-年轻)",
            "zh-CN-XiaoyiNeural (晓伊-女声-活泼)",
            "zh-CN-YunjianNeural (云健-男声-沉稳)",
            "en-US-JennyNeural (Jenny-English)",
            "en-US-GuyNeural (Guy-English)",
            "ja-JP-NanamiNeural (七海-日本語)"
        );
    }

    private String mapVoice(String voice) {
        if (voice == null || voice.isBlank()) return "zh-CN-XiaoxiaoNeural";
        return switch (voice.toLowerCase()) {
            case "晓晓", "xiaoxiao", "zh-cn-xiaoxiaoneural" -> "zh-CN-XiaoxiaoNeural";
            case "云扬", "yunyang", "zh-cn-yunyangneural" -> "zh-CN-YunyangNeural";
            case "云希", "yunxi", "zh-cn-yunxineural" -> "zh-CN-YunxiNeural";
            case "晓伊", "xiaoyi" -> "zh-CN-XiaoyiNeural";
            case "云健", "yunjian" -> "zh-CN-YunjianNeural";
            case "jenny" -> "en-US-JennyNeural";
            case "guy" -> "en-US-GuyNeural";
            case "nanami", "七海" -> "ja-JP-NanamiNeural";
            default -> "zh-CN-XiaoxiaoNeural";
        };
    }

    private String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
