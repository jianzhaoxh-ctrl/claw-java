package com.openclaw.desktop.rag;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmRequest;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检索增强生成引擎 (RAG)。
 * 对应 OpenClaw 的 context engine + memory search 组合。
 *
 * <p>工作流程：
 * <ol>
 *   <li>将用户查询嵌入为向量</li>
 *   <li>在 VectorStore 中检索相关文档片段</li>
 *   <li>将检索结果注入到 LLM 请求的系统提示中</li>
 *   <li>LLM 基于增强上下文生成回答</li>
 * </ol>
 */
public class RAGEngine {

    private static final Logger log = LoggerFactory.getLogger(RAGEngine.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_MIN_SCORE = 0.5;

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final LlmProvider llmProvider;
    private final String chatModelId;

    public RAGEngine(EmbeddingService embeddingService, VectorStore vectorStore,
                     LlmProvider llmProvider, String chatModelId) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.llmProvider = llmProvider;
        this.chatModelId = chatModelId;
    }

    /** RAG 查询 — 检索 + 生成 */
    public Mono<RAGResult> query(String userQuery, RAGConfig config) {
        log.info("RAG query: queryLen={}, topK={}", userQuery.length(), config.topK());

        // 1. 嵌入查询
        return embeddingService.embed(userQuery)
            .flatMap(queryVector -> {
                // 2. 检索相关文档
                return vectorStore.search(queryVector, config.topK(), config.collection())
                    .filter(sr -> sr.score() >= config.minScore())
                    .collectList()
                    .flatMap(searchResults -> {
                        if (searchResults.isEmpty()) {
                            log.info("RAG: no relevant documents found");
                            // 无检索结果时直接调用 LLM
                            return llmProvider.chat(buildRequest(userQuery, List.of(), config))
                                .map(resp -> new RAGResult(resp, List.of(), 0));
                        }

                        log.info("RAG: found {} relevant documents", searchResults.size());
                        // 3. 注入检索结果到 LLM 请求
                        return llmProvider.chat(buildRequest(userQuery, searchResults, config))
                            .map(resp -> new RAGResult(resp,
                                searchResults.stream().map(sr -> sr.entry()).toList(),
                                searchResults.size()));
                    });
            });
    }

    /** 仅检索 — 不调用 LLM */
    public Flux<VectorStore.SearchResult> retrieveOnly(String query, int topK) {
        return embeddingService.embed(query)
            .flatMapMany(queryVector -> vectorStore.search(queryVector, topK));
    }

    /** 索引文档 — 分块 + 嵌入 + 存储 */
    public Mono<Void> indexDocument(String docId, String content, Map<String, String> metadata, ChunkConfig chunkConfig) {
        log.info("Indexing document: docId={}, contentLen={}", docId, content.length());

        // 1. 文本分块
        var chunks = chunkText(content, chunkConfig);

        // 2. 嵌入每个块
        return Flux.fromIterable(chunks)
            .index()
            .flatMap(tuple -> {
                var chunkId = docId + "_chunk_" + tuple.getT1();
                var chunkText = tuple.getT2();
                var chunkMeta = new java.util.HashMap<>(metadata);
                chunkMeta.put("docId", docId);
                chunkMeta.put("chunkIndex", String.valueOf(tuple.getT1()));

                return embeddingService.embed(chunkText)
                    .flatMap(vector -> vectorStore.add(chunkId, vector, chunkText, chunkMeta));
            })
            .then();
    }

    /** 删除文档的所有块 */
    public Mono<Void> deleteDocument(String docId) {
        // 查找所有属于该文档的块并删除
        var toDelete = new ArrayList<String>();
        // 目前内存存储没有高效按 docId 查询的方法，后续 SQLite 版本会改进
        return Mono.empty(); // TODO: 实现批量删除
    }

    // ========== 构建请求 ==========

    private LlmRequest buildRequest(String userQuery, List<VectorStore.SearchResult> searchResults, RAGConfig config) {
        var systemPrompt = config.systemPrompt();
        if (!searchResults.isEmpty()) {
            var contextBuilder = new StringBuilder(systemPrompt);
            contextBuilder.append("\n\n--- Retrieved Context ---\n");
            for (var sr : searchResults) {
                contextBuilder.append(String.format("[Source: %s | Score: %.2f]\n%s\n\n",
                    sr.entry().id(), sr.score(), sr.entry().text()));
            }
            contextBuilder.append("--- End of Context ---\n\n");
            contextBuilder.append("Based on the above context, answer the user's question. ");
            contextBuilder.append("If the context doesn't contain relevant information, say so honestly.");
            systemPrompt = contextBuilder.toString();
        }

        return new LlmRequest(
            chatModelId,
            List.of(
                new Message.SystemMessage(systemPrompt),
                new Message.UserMessage(userQuery)
            ),
            config.temperature(),
            config.maxTokens(),
            null, List.of(), null, Map.of()
        );
    }

    // ========== 文本分块 ==========

    private List<String> chunkText(String content, ChunkConfig config) {
        var chunks = new ArrayList<String>();
        var remaining = content;

        while (remaining.length() > config.chunkSize()) {
            // 找到最近的句子/段落边界
            var splitPos = findSplitPosition(remaining, config.chunkSize(), config.overlap());
            chunks.add(remaining.substring(0, splitPos));
            remaining = remaining.substring(Math.max(0, splitPos - config.overlap()));
        }

        if (!remaining.isBlank()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private int findSplitPosition(String text, int chunkSize, int overlap) {
        var target = Math.min(chunkSize, text.length());
        // 找到最近的换行符或句号
        for (int i = target; i > target / 2; i--) {
            if (i < text.length() && (text.charAt(i) == '\n' || text.charAt(i) == '.' || text.charAt(i) == '。')) {
                return i + 1;
            }
        }
        return target;
    }

    // ========== 配置 ==========

    public record RAGConfig(
        int topK,
        double minScore,
        String collection,
        String systemPrompt,
        Double temperature,
        Integer maxTokens
    ) {
        public static RAGConfig defaults() {
            return new RAGConfig(DEFAULT_TOP_K, DEFAULT_MIN_SCORE, null,
                "You are a helpful AI assistant with access to retrieved context.",
                0.3, 4096);
        }
    }

    public record ChunkConfig(
        int chunkSize,
        int overlap
    ) {
        public static ChunkConfig defaults() {
            return new ChunkConfig(512, 64);
        }
    }

    // ========== 结果 ==========

    public record RAGResult(
        com.openclaw.desktop.llm.LlmResponse llmResponse,
        List<VectorStore.VectorEntry> sources,
        int contextCount
    ) {}
}
