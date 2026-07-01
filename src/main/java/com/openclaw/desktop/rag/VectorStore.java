package com.openclaw.desktop.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 向量存储 — 存储和检索嵌入向量。
 * 对应 OpenClaw 的 memory search 底层存储。
 *
 * <p>支持：
 * <ul>
 *   <li>内存存储（ConcurrentHashMap）</li>
 *   <li>余弦相似度搜索</li>
 *   <li>按集合/标签过滤</li>
 * </ul>
 *
 * <p>未来可扩展到 SQLite + 向量索引。
 */
public class VectorStore {

    private static final Logger log = LoggerFactory.getLogger(VectorStore.class);
    private final int dimensions;
    private final Map<String, VectorEntry> store = new ConcurrentHashMap<>();

    public VectorStore(int dimensions) {
        this.dimensions = dimensions;
    }

    /** 添加向量 */
    public Mono<Void> add(String id, float[] vector, String text, Map<String, String> metadata) {
        return Mono.fromRunnable(() -> {
            if (vector.length != dimensions) {
                throw new IllegalArgumentException(
                    "Vector dimension mismatch: expected " + dimensions + ", got " + vector.length);
            }
            store.put(id, new VectorEntry(id, vector, text, metadata, System.currentTimeMillis()));
            log.debug("Vector added: id={}, textLen={}", id, text.length());
        });
    }

    /** 批量添加 */
    public Mono<Void> addBatch(List<VectorEntry> entries) {
        return Flux.fromIterable(entries)
            .flatMap(e -> add(e.id(), e.vector(), e.text(), e.metadata()))
            .then();
    }

    /** 删除向量 */
    public Mono<Void> delete(String id) {
        return Mono.fromRunnable(() -> store.remove(id));
    }

    /** 搜索最相似的向量 */
    public Flux<SearchResult> search(float[] queryVector, int topK, String collection) {
        return Flux.fromIterable(store.values())
            .filter(e -> collection == null || collection.equals(e.metadata().get("collection")))
            .map(e -> new SearchResult(e, cosineSimilarity(queryVector, e.vector())))
            .sort((a, b) -> Double.compare(b.score(), a.score()))
            .take(topK);
    }

    /** 搜索最相似的向量（无集合过滤） */
    public Flux<SearchResult> search(float[] queryVector, int topK) {
        return search(queryVector, topK, null);
    }

    /** 获取向量 */
    public Optional<VectorEntry> get(String id) {
        return Optional.ofNullable(store.get(id));
    }

    /** 向量数量 */
    public int size() { return store.size(); }

    /** 清空 */
    public Mono<Void> clear() {
        return Mono.fromRunnable(store::clear);
    }

    // ========== 余弦相似度 ==========

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ========== 内部类型 ==========

    public record VectorEntry(
        String id,
        float[] vector,
        String text,
        Map<String, String> metadata,
        long timestamp
    ) {}

    public record SearchResult(
        VectorEntry entry,
        double score
    ) {}
}
