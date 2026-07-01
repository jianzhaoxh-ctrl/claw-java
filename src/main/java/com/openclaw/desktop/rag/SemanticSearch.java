package com.openclaw.desktop.rag;

import com.openclaw.desktop.memory.MemoryDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语义搜索 — 基于 embedding 的记忆搜索。
 * 对应 OpenClaw 的 memory_search 工具。
 *
 * <p>与 MemoryDatabase 集成，提供：
 * <ul>
 *   <li>语义搜索（基于向量相似度）</li>
 *   <li>关键词搜索（基于 SQLite FTS）</li>
 *   <li>混合搜索（结合语义+关键词）</li>
 * </ul>
 */
public class SemanticSearch {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearch.class);
    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final MemoryDatabase memoryDatabase;

    public SemanticSearch(EmbeddingService embeddingService, VectorStore vectorStore,
                          MemoryDatabase memoryDatabase) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.memoryDatabase = memoryDatabase;
    }

    /** 语义搜索 — 纯向量相似度 */
    public Flux<SearchHit> semanticSearch(String query, int topK) {
        return embeddingService.embed(query)
            .flatMapMany(queryVector ->
                vectorStore.search(queryVector, topK)
                    .map(sr -> new SearchHit(
                        sr.entry().id(),
                        sr.entry().text(),
                        sr.score(),
                        "semantic",
                        sr.entry().metadata()
                    ))
            );
    }

    /** 关键词搜索 — 纯文本匹配 */
    public Flux<SearchHit> keywordSearch(String query, int topK) {
        try {
            var entries = memoryDatabase.search(query, topK);
            return Flux.fromIterable(entries)
                .map(entry -> new SearchHit(
                    entry.id(),
                    entry.content(),
                    1.0,
                    "keyword",
                    Map.of("sessionKey", entry.sessionKey() != null ? entry.sessionKey() : "", "createdAt", entry.createdAt() != null ? entry.createdAt().toString() : ""))
                );
        } catch (Exception e) {
            return Flux.empty();
        }
    }

    /** 混合搜索 — 语义+关键词加权 */
    public Flux<SearchHit> hybridSearch(String query, int topK, double semanticWeight, double keywordWeight) {
        var semanticHits = semanticSearch(query, topK * 2).collectList();
        var keywordHits = keywordSearch(query, topK * 2).collectList();

        return Mono.zip(semanticHits, keywordHits)
            .flatMapMany(tuple -> {
                var semantic = tuple.getT1();
                var keyword = tuple.getT2();

                // 合并结果，去重，加权排序
                var merged = new LinkedHashMap<String, SearchHit>();
                for (var hit : semantic) {
                    var weightedScore = hit.score() * semanticWeight;
                    merged.merge(hit.id(),
                        new SearchHit(hit.id(), hit.content(), weightedScore, "hybrid-semantic", hit.metadata()),
                        (existing, newHit) -> new SearchHit(existing.id(), existing.content(),
                            existing.score() + weightedScore, "hybrid", existing.metadata()));
                }
                for (var hit : keyword) {
                    var weightedScore = hit.score() * keywordWeight;
                    merged.merge(hit.id(),
                        new SearchHit(hit.id(), hit.content(), weightedScore, "hybrid-keyword", hit.metadata()),
                        (existing, newHit) -> new SearchHit(existing.id(), existing.content(),
                            existing.score() + weightedScore, "hybrid", existing.metadata()));
                }

                return Flux.fromIterable(merged.values())
                    .sort((a, b) -> Double.compare(b.score(), a.score()))
                    .take(topK);
            });
    }

    /** 索引记忆条目到向量存储 */
    public Mono<Void> indexMemoryEntry(String id, String content, Map<String, String> metadata) {
        return embeddingService.embed(content)
            .flatMap(vector -> vectorStore.add(id, vector, content, metadata));
    }

    /** 批量索引所有记忆 */
    public Mono<Void> indexAllMemory() {
        try {
            var entries = memoryDatabase.recent(10000);
            return Flux.fromIterable(entries)
                .flatMap(entry -> indexMemoryEntry(
                    entry.id(),
                    entry.content(),
                    Map.of("sessionKey", entry.sessionKey() != null ? entry.sessionKey() : "", "createdAt", entry.createdAt() != null ? entry.createdAt().toString() : ""))
                )
                .then();
        } catch (Exception e) {
            return Mono.empty();
        }
    }

    // ========== 搜索结果 ==========

    public record SearchHit(
        String id,
        String content,
        double score,
        String searchType,
        Map<String, String> metadata
    ) {}
}
