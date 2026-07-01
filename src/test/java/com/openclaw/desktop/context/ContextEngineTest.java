package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ContextEngine 单元测试。
 */
class ContextEngineTest {

    @Test
    void testTokenEstimatorEnglish() {
        var estimator = new TokenEstimator();
        // 英文：约 4 chars/token
        var tokens = estimator.estimateText("Hello world, this is a test message");
        assertTrue(tokens > 0);
        assertTrue(tokens < 20, "Short English text should have few tokens");
    }

    @Test
    void testTokenEstimatorChinese() {
        var estimator = new TokenEstimator();
        // 中文：约 1.5 chars/token
        var tokens = estimator.estimateText("你好世界，这是一个测试消息");
        assertTrue(tokens > 0);
        // 14 chars / 1.5 ≈ 9 tokens
        assertTrue(tokens >= 5 && tokens <= 15, "Chinese text tokens should be reasonable");
    }

    @Test
    void testTokenEstimatorMixed() {
        var estimator = new TokenEstimator();
        var tokens = estimator.estimateText("Hello 你好 world 世界");
        assertTrue(tokens > 0);
    }

    @Test
    void testTokenEstimatorEmpty() {
        var estimator = new TokenEstimator();
        assertEquals(0, estimator.estimateText(""));
        assertEquals(0, estimator.estimateText(null));
    }

    @Test
    void testEstimateMessage() {
        var estimator = new TokenEstimator();
        var msg = new Message.UserMessage("Hello, how are you?");
        var tokens = estimator.estimateMessage(msg);
        assertTrue(tokens > 0, "Message should have positive token estimate");
    }

    @Test
    void testEstimateConversation() {
        var estimator = new TokenEstimator();
        var messages = java.util.List.<Message>of(
            new Message.SystemMessage("You are a helpful assistant."),
            new Message.UserMessage("Hello"),
            new Message.AssistantMessage("Hi there!", java.util.List.of())
        );
        var tokens = estimator.estimateConversation(messages);
        assertTrue(tokens > 0, "Conversation should have positive token estimate");
    }

    @Test
    void testAssembleNoCompressionNeeded() {
        var engine = new ContextEngine();
        var config = ContextEngineConfig.defaults();
        var messages = java.util.List.<Message>of(
            new Message.SystemMessage("Be helpful."),
            new Message.UserMessage("Hi")
        );
        var result = engine.assemble(messages, config);
        assertFalse(result.wasCompacted(), "Short conversation should not need compression");
        assertFalse(result.wasTruncated());
        assertEquals(2, result.messages().size());
    }

    @Test
    void testCompact() {
        var engine = new ContextEngine();
        var messages = new java.util.ArrayList<Message>();
        messages.add(new Message.SystemMessage("You are a helpful assistant."));
        // 添加 20 条模拟消息
        for (int i = 0; i < 20; i++) {
            messages.add(new Message.UserMessage("Message " + i + ": This is a test message with some content."));
            messages.add(new Message.AssistantMessage("Response " + i, java.util.List.of()));
        }

        var config = com.openclaw.desktop.context.CompactConfig.defaults();
        var result = engine.compact(messages, config);

        assertNotNull(result, "Compact result should not be null");
        assertNotNull(result.summaryMessage(), "Summary message should be created");
        assertTrue(result.compactedTokens() < result.originalTokens(),
            "Compacted tokens should be less than original");
    }

    @Test
    void testNeedsCompact() {
        var engine = new ContextEngine();
        var shortMessages = java.util.List.<Message>of(
            new Message.UserMessage("Hello")
        );
        assertFalse(engine.needsCompact(shortMessages, 100000), "Short conversation should not need compact");

        var longMessages = new java.util.ArrayList<Message>();
        for (int i = 0; i < 100; i++) {
            longMessages.add(new Message.UserMessage("This is message number " + i + " with some content."));
        }
        assertTrue(engine.needsCompact(longMessages, 100), "Long conversation should need compact");
    }
}
