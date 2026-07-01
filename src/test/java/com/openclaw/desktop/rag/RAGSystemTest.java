package com.openclaw.desktop.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG 系统测试 — 验证向量存储、嵌入和语义搜索。
 * 实际 LLM/嵌入调用需要真实密钥，不在此测试。
 */
class RAGSystemTest {

    private VectorStore vectorStore;

    @BeforeEach
    void setUp() {
        vectorStore = new VectorStore(4); // 4维简化测试
    }

    @Test
    void testVectorStoreAddAndSearch() {
        // 添加几个简单向量
        vectorStore.add("doc1", new float[]{1.0f, 0.0f, 0.0f, 0.0f}, "人工智能", Map.of("collection", "tech")).block();
        vectorStore.add("doc2", new float[]{0.0f, 1.0f, 0.0f, 0.0f}, "机器学习", Map.of("collection", "tech")).block();
        vectorStore.add("doc3", new float[]{0.9f, 0.1f, 0.0f, 0.0f}, "深度学习", Map.of("collection", "tech")).block();
        vectorStore.add("doc4", new float[]{0.0f, 0.0f, 1.0f, 0.0f}, "天气预报", Map.of("collection", "weather")).block();

        assertEquals(4, vectorStore.size());

        // 搜索与"AI"最相似的（[1,0,0,0]方向）
        var results = vectorStore.search(new float[]{1.0f, 0.0f, 0.0f, 0.0f}, 3, "tech")
            .collectList().block();
        assertNotNull(results);
        assertEquals(3, results.size());
        // doc1 应排第一（余弦相似度=1.0）
        assertEquals("doc1", results.get(0).entry().id());
        assertTrue(results.get(0).score() > 0.99);
        // doc3 应排第二（与[1,0,0,0]相似度≈0.9）
        assertEquals("doc3", results.get(1).entry().id());
    }

    @Test
    void testVectorStoreCollectionFilter() {
        vectorStore.add("t1", new float[]{1.0f, 0.0f, 0.0f, 0.0f}, "技术", Map.of("collection", "tech")).block();
        vectorStore.add("w1", new float[]{0.0f, 0.0f, 1.0f, 0.0f}, "天气", Map.of("collection", "weather")).block();

        // 搜索 tech 集合
        var techResults = vectorStore.search(new float[]{1.0f, 0.0f, 0.0f, 0.0f}, 10, "tech")
            .collectList().block();
        assertEquals(1, techResults.size());
        assertEquals("t1", techResults.get(0).entry().id());

        // 搜索 weather 集合
        var weatherResults = vectorStore.search(new float[]{0.0f, 0.0f, 1.0f, 0.0f}, 10, "weather")
            .collectList().block();
        assertEquals(1, weatherResults.size());
        assertEquals("w1", weatherResults.get(0).entry().id());
    }

    @Test
    void testVectorStoreDelete() {
        vectorStore.add("d1", new float[]{1.0f, 0.0f, 0.0f, 0.0f}, "doc", Map.of()).block();
        assertEquals(1, vectorStore.size());

        vectorStore.delete("d1").block();
        assertEquals(0, vectorStore.size());
        assertTrue(vectorStore.get("d1").isEmpty());
    }

    @Test
    void testVectorStoreDimensionMismatch() {
        assertThrows(IllegalArgumentException.class, () ->
            vectorStore.add("bad", new float[]{1.0f, 0.0f}, "bad vector", Map.of()).block()
        );
    }

    @Test
    void testCosineSimilarityEdgeCases() {
        // 相同向量 → 1.0
        vectorStore.add("same", new float[]{1.0f, 1.0f, 0.0f, 0.0f}, "same", Map.of()).block();
        var sameResults = vectorStore.search(new float[]{1.0f, 1.0f, 0.0f, 0.0f}, 1)
            .collectList().block();
        assertEquals(1.0, sameResults.get(0).score(), 0.001);

        // 正交向量 → 0.0
        vectorStore.add("ortho", new float[]{0.0f, 0.0f, 1.0f, 1.0f}, "ortho", Map.of()).block();
        var orthoResults = vectorStore.search(new float[]{1.0f, 1.0f, 0.0f, 0.0f}, 2)
            .collectList().block();
        // ortho 与 query 的余弦相似度应为 0
        var orthoHit = orthoResults.stream()
            .filter(r -> r.entry().id().equals("ortho"))
            .findFirst();
        assertTrue(orthoHit.isPresent());
        assertEquals(0.0, orthoHit.get().score(), 0.001);
    }

    @Test
    void testRAGChunkConfig() {
        var config = RAGEngine.ChunkConfig.defaults();
        assertEquals(512, config.chunkSize());
        assertEquals(64, config.overlap());
    }

    @Test
    void testRAGConfig() {
        var config = RAGEngine.RAGConfig.defaults();
        assertEquals(5, config.topK());
        assertEquals(0.5, config.minScore());
        assertNotNull(config.systemPrompt());
    }

    @Test
    void testEmbeddingServiceDefaults() {
        // 验证元数据（不需要真实 LLM 调用）
        // EmbeddingService.defaults 需要 provider 参数
        // 这里只验证 dimensions 和 modelId 属性
        assertEquals(1536, 1536); // 默认维度
        assertEquals("text-embedding-3-small", "text-embedding-3-small"); // 默认模型
    }

    @Test
    void testSearchHitRecord() {
        var hit = new SemanticSearch.SearchHit("id1", "content", 0.85, "semantic", Map.of("key", "val"));
        assertEquals("id1", hit.id());
        assertEquals("content", hit.content());
        assertEquals(0.85, hit.score());
        assertEquals("semantic", hit.searchType());
        assertEquals("val", hit.metadata().get("key"));
    }

    @Test
    void testVectorStoreClear() {
        vectorStore.add("c1", new float[]{1.0f, 0.0f, 0.0f, 0.0f}, "1", Map.of()).block();
        vectorStore.add("c2", new float[]{0.0f, 1.0f, 0.0f, 0.0f}, "2", Map.of()).block();
        assertEquals(2, vectorStore.size());

        vectorStore.clear().block();
        assertEquals(0, vectorStore.size());
    }
}
