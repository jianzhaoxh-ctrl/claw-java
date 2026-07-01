package com.openclaw.desktop.rag;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmRequest;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.Model;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 向量嵌入服务 — 将文本转换为向量表示。
 * 对应 OpenClaw 的 embedding 调用逻辑。
 *
 * <p>支持：
 * <ul>
 *   <li>OpenAI text-embedding-3-small/large</li>
 *   <li>本地 Ollama embedding</li>
 *   <li>Qwen embedding</li>
 * </ul>
 */
public class EmbeddingService {

    private final LlmProvider embeddingProvider;
    private final String embeddingModelId;
    private final int dimensions;

    public EmbeddingService(LlmProvider embeddingProvider, String embeddingModelId, int dimensions) {
        this.embeddingProvider = embeddingProvider;
        this.embeddingModelId = embeddingModelId;
        this.dimensions = dimensions;
    }

    /** 默认配置 — OpenAI text-embedding-3-small, 1536维 */
    public static EmbeddingService defaults(LlmProvider provider) {
        return new EmbeddingService(provider, "text-embedding-3-small", 1536);
    }

    /** 嵌入单条文本 */
    public Mono<float[]> embed(String text) {
        if (text == null || text.isBlank()) {
            return Mono.just(new float[dimensions]);
        }
        return embeddingProvider.chat(new LlmRequest(
            embeddingModelId,
            List.of(new Message.UserMessage("Embed this text: " + truncate(text, 8000))),
            null, null, null, List.of(), null, java.util.Map.of("embedding", true)
        ))
        .map(resp -> parseEmbedding(resp.text()));
    }

    /** 批量嵌入 */
    public Flux<float[]> embedBatch(List<String> texts) {
        return Flux.fromIterable(texts)
            .flatMap(this::embed);
    }

    /** 嵌入模型元数据 */
    public String modelId() { return embeddingModelId; }
    public int dimensions() { return dimensions; }

    // ========== 解析 ==========

    private float[] parseEmbedding(String content) {
        // 如果 Provider 返回 JSON 格式的向量
        if (content != null && content.startsWith("[") && content.endsWith("]")) {
            try {
                var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                var array = mapper.readValue(content, float[].class);
                if (array.length == dimensions) return array;
                // 维度不匹配时缩放/截断
                var result = new float[dimensions];
                System.arraycopy(array, 0, result, 0, Math.min(array.length, dimensions));
                return result;
            } catch (Exception e) {
                // 解析失败，返回零向量
            }
        }
        // 非向量格式返回零向量（表示不支持嵌入）
        return new float[dimensions];
    }

    private String truncate(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen);
    }
}
