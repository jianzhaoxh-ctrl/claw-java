package com.openclaw.desktop.context;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文引擎 — 管理 conversation token 限制和压缩。
 *
 * <p>对应 OpenClaw 的 ContextEngine（src/context-engine/）。
 * 核心功能：
 * <ul>
 *   <li>Token 估算</li>
 *   <li>自动压缩（超过阈值时）</li>
 *   <li>滑动窗口截断</li>
 * </ul>
 */
public class ContextEngine {

    private static final Logger log = LoggerFactory.getLogger(ContextEngine.class);
    private final TokenEstimator estimator = new TokenEstimator();
    private final ContextEngineConfig config;

    public ContextEngine() {
        this(ContextEngineConfig.defaults());
    }

    public ContextEngine(ContextEngineConfig config) {
        this.config = config;
    }

    /**
     * 组装对话上下文 — 根据配置截断或压缩。
     */
    public AssembleResult assemble(List<Message> messages, ContextEngineConfig assembleConfig) {
        int estimatedTokens = estimator.estimateConversation(messages);

        // 未超阈值 → 直接返回
        if (estimatedTokens <= assembleConfig.maxTokens()) {
            return AssembleResult.of(messages, estimatedTokens);
        }

        // 超阈值 → 需要压缩
        log.info("Context exceeds limit: estimated={}, max={}, compressing...",
            estimatedTokens, assembleConfig.maxTokens());
        var compactResult = compact(messages, CompactConfig.defaults());

        var resultMessages = new ArrayList<Message>();
        // 保留 system 提示
        for (var msg : messages) {
            if (msg instanceof Message.SystemMessage) {
                resultMessages.add(msg);
                break;
            }
        }
        // 压缩后的摘要 + 消息
        var summary = compactResult.summaryMessage();
        if (summary != null) resultMessages.add(summary);
        for (var msg : compactResult.compactedMessages()) resultMessages.add(msg);

        int resultTokens = estimator.estimateConversation(resultMessages);

        return new AssembleResult(
            resultMessages, resultTokens,
            summary != null ? "auto-compression applied" : "",
            summary != null ? "compacted" : "truncated",
            true,
            true,
            "token-limit exceeded"
        );
    }

    /**
     * 压缩对话 — 使用滑动窗口 + 摘要策略。
     */
    public CompactResult compact(List<Message> messages, CompactConfig compactConfig) {
        int totalTokens = estimator.estimateConversation(messages);

        // 1. 分离系统提示
        var systemMessages = new ArrayList<Message>();
        var otherMessages = new ArrayList<Message>();
        for (var msg : messages) {
            if (msg instanceof Message.SystemMessage) {
                systemMessages.add(msg);
            } else {
                otherMessages.add(msg);
            }
        }

        // 2. 保留最近 N 条消息
        var keepCount = compactConfig.keepLastN();
        var keptMessages = new ArrayList<Message>();
        var compactedMessages = new ArrayList<Message>();
        if (otherMessages.size() > keepCount) {
            for (int i = 0; i < otherMessages.size() - keepCount; i++) {
                compactedMessages.add(otherMessages.get(i));
            }
            for (int i = Math.max(0, otherMessages.size() - keepCount); i < otherMessages.size(); i++) {
                keptMessages.add(otherMessages.get(i));
            }
        } else {
            compactedMessages.addAll(otherMessages);
        }

        // 3. 创建摘要消息
        var summary = createSummary(compactedMessages);

        // 4. 重新组装
        var resultMessages = new ArrayList<Message>();
        resultMessages.addAll(systemMessages);
        resultMessages.add(summary);
        resultMessages.addAll(keptMessages);

        int resultTokens = estimator.estimateConversation(resultMessages);

        return CompactResult.success(resultMessages, summary, totalTokens, resultTokens);
    }

    /**
     * 创建压缩摘要 — 将多条消息合并为一条 SystemMessage。
     */
    private Message createSummary(List<Message> messages) {
        var sb = new StringBuilder();
        sb.append("[对话历史摘要]\n");
        for (var msg : messages) {
            var role = msg.role();
            var text = MessageContent.extractText(msg.content());
            sb.append(role).append(": ");
            if (text != null && text.length() > 200) {
                sb.append(text.substring(0, 200)).append("...");
            } else {
                sb.append(text != null ? text : "");
            }
            sb.append("\n");
        }
        sb.append("\n请基于以上摘要继续对话，不要重复已说过的内容。");

        return new Message.SystemMessage(sb.toString());
    }

    /**
     * 检查对话是否需要压缩。
     */
    public boolean needsCompact(List<Message> messages, int maxTokens) {
        return estimator.estimateConversation(messages) > maxTokens;
    }

    /**
     * 获取 token 估算器。
     */
    public TokenEstimator estimator() { return estimator; }
}
