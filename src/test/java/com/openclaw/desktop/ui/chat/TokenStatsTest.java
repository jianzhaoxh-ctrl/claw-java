package com.openclaw.desktop.ui.chat;

import com.openclaw.desktop.llm.UsageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 统计计算测试（不含 JavaFX UI 部分）。
 */
class TokenStatsTest {

    @Test
    @DisplayName("estimateCost calculation")
    void testEstimateCost() {
        var cost = TokenStats.estimateCost("gpt-4o", 100, 50);
        // gpt-4o: input $2.5/1k, output $10/1k
        // cost = (100 * 2.5 / 1000) + (50 * 10 / 1000) = 0.25 + 0.50 = 0.75
        assertTrue(cost > 0);
        assertTrue(cost < 100);
    }

    @Test
    @DisplayName("estimateCost for unknown model uses default pricing")
    void testEstimateCostUnknownModel() {
        var cost = TokenStats.estimateCost("unknown-model", 100, 50);
        assertTrue(cost > 0);
    }

    @Test
    @DisplayName("ModelPricing for known models")
    void testModelPricing() {
        var gpt4o = TokenStats.ModelPricing.get("gpt-4o");
        assertEquals(2.5, gpt4o.inputCostPer1k());
        assertEquals(10.0, gpt4o.outputCostPer1k());

        var claude35 = TokenStats.ModelPricing.get("claude-3.5-sonnet");
        assertEquals(3.0, claude35.inputCostPer1k());
        assertEquals(15.0, claude35.outputCostPer1k());

        var deepseek = TokenStats.ModelPricing.get("deepseek-chat");
        assertEquals(0.14, deepseek.inputCostPer1k());
        assertEquals(0.28, deepseek.outputCostPer1k());
    }

    @Test
    @DisplayName("ModelPricing default for null")
    void testModelPricingNull() {
        var pricing = TokenStats.ModelPricing.get(null);
        assertNotNull(pricing);
        assertEquals(0.01, pricing.inputCostPer1k());
    }
}
