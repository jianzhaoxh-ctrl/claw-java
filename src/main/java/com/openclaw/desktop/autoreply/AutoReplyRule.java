package com.openclaw.desktop.autoreply;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 自动回复规则。
 * 对应 OpenClaw 的 auto-reply。
 *
 * 规则类型：
 * - KEYWORD: 关键词匹配（包含即触发）
 * - REGEX: 正则表达式匹配
 * - COMMAND: 命令匹配（以 / 开头精确匹配）
 * - CONDITION: 条件表达式（如 time=09:00-18:00）
 */
public record AutoReplyRule(
    String id,
    String name,
    RuleType type,
    String pattern,        // 匹配模式
    String reply,          // 回复内容（支持模板变量）
    double matchScore,     // 匹配权重
    boolean enabled,
    String channelId,      // 限定通道（null=所有通道）
    List<String> tags      // 标签
) {
    public enum RuleType { KEYWORD, REGEX, COMMAND, CONDITION }

    public static AutoReplyRule keyword(String name, String keyword, String reply) {
        return new AutoReplyRule(
            java.util.UUID.randomUUID().toString().substring(0, 8),
            name, RuleType.KEYWORD, keyword, reply, 1.0, true, null, List.of()
        );
    }

    public static AutoReplyRule command(String name, String cmd, String reply) {
        return new AutoReplyRule(
            java.util.UUID.randomUUID().toString().substring(0, 8),
            name, RuleType.COMMAND, cmd, reply, 2.0, true, null, List.of()
        );
    }

    public static AutoReplyRule regex(String name, String regex, String reply) {
        return new AutoReplyRule(
            java.util.UUID.randomUUID().toString().substring(0, 8),
            name, RuleType.REGEX, regex, reply, 1.5, true, null, List.of()
        );
    }
}
