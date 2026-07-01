package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;

/**
 * Token 估算器 — 基于规则的经验估算，无需实际调用 LLM。
 *
 * <p>对应 OpenClaw 的 token-estimator.ts。
 * 使用字符数比例估算 token 数，不同语言有不同的比例：
 * <ul>
 *   <li>英文：约 4 字符 ≈ 1 token</li>
 *   <li>中文：约 1.5 字符 ≈ 1 token</li>
 *   <li>代码：约 3 字符 ≈ 1 token</li>
 * </ul>
 */
public class TokenEstimator {

    // 英文约 4 字符/token，中文约 1.5 字符/token，混合取中间值
    private static final double CHARS_PER_TOKEN_EN = 4.0;
    private static final double CHARS_PER_TOKEN_ZH = 1.5;
    private static final double CHARS_PER_TOKEN_MIXED = 2.5;

    // 消息固定开销（role + formatting）
    private static final int MESSAGE_OVERHEAD = 4;

    // 工具调用额外开销
    private static final int TOOL_CALL_OVERHEAD = 15;

    /**
     * 估算单条消息的 token 数。
     */
    public int estimateMessage(Message message) {
        int tokens = MESSAGE_OVERHEAD;

        for (var content : message.content()) {
            tokens += estimateContent(content);
        }

        // 工具结果消息额外开销
        if (message instanceof Message.ToolResultMessage) {
            tokens += 6;
        }

        return tokens;
    }

    /**
     * 估算单个内容块的 token 数。
     */
    public int estimateContent(MessageContent content) {
        return switch (content) {
            case MessageContent.TextContent(var text) -> estimateText(text);
            case MessageContent.ThinkingContent(var thinking, var sig, var redacted) -> estimateText(thinking) + (sig != null ? 10 : 0);
            case MessageContent.ImageContent(var data, var mime) -> estimateImageTokens(mime);
            case MessageContent.ToolCallContent(var id, var name, var args, var sig, var mode) ->
                TOOL_CALL_OVERHEAD + estimateText(name) + estimateText(args) + (sig != null ? 10 : 0);
        };
    }

    /**
     * 估算文本的 token 数。
     */
    public int estimateText(String text) {
        if (text == null || text.isEmpty()) return 0;

        // 判断中英文比例
        int chineseChars = 0;
        int totalChars = text.length();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) chineseChars++;
        }

        double ratio = (double) chineseChars / totalChars;

        // 混合估算
        double charsPerToken;
        if (ratio > 0.6) {
            charsPerToken = CHARS_PER_TOKEN_ZH;
        } else if (ratio < 0.1) {
            charsPerToken = CHARS_PER_TOKEN_EN;
        } else {
            charsPerToken = CHARS_PER_TOKEN_MIXED;
        }

        return (int) Math.ceil(totalChars / charsPerToken);
    }

    /**
     * 估算整个对话的 token 数。
     */
    public int estimateConversation(java.util.List<Message> messages) {
        int total = 0;
        for (var msg : messages) {
            total += estimateMessage(msg);
        }
        return total;
    }

    /**
     * 估算图片的 token 数（粗略）。
     */
    private int estimateImageTokens(String mimeType) {
        // OpenAI 图片 token 估算：低分辨率约 85，高分辨率约 170-1105
        // 简化处理
        return 170;
    }

    /**
     * 判断是否为中文字符。
     */
    private boolean isChinese(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            || block == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}
