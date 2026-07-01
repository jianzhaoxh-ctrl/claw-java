package com.openclaw.desktop.autoreply;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 自动回复引擎 — 匹配入站消息并触发自动回复。
 * 对应 OpenClaw 的 auto-reply 模块（560 TS）。
 *
 * 功能：
 * - 规则注册/删除/启用/禁用
 * - 按优先级匹配（COMMAND > REGEX > KEYWORD > CONDITION）
 * - 模板变量替换（{user}, {time}, {date}, {channel}）
 * - 通道隔离
 * - 时间条件
 */
public class AutoReplyEngine {

    private static final Logger log = LoggerFactory.getLogger(AutoReplyEngine.class);

    private final List<AutoReplyRule> rules = new ArrayList<>();
    private final Map<String, Boolean> ruleStates = new ConcurrentHashMap<>();

    /**
     * 注册规则。
     */
    public void register(AutoReplyRule rule) {
        rules.add(rule);
        ruleStates.put(rule.id(), rule.enabled());
        log.debug("Auto-reply rule registered: {} ({})", rule.name(), rule.type());
    }

    /**
     * 删除规则。
     */
    public boolean unregister(String ruleId) {
        var removed = rules.removeIf(r -> r.id().equals(ruleId));
        ruleStates.remove(ruleId);
        return removed;
    }

    /**
     * 启用/禁用规则。
     */
    public void setEnabled(String ruleId, boolean enabled) {
        ruleStates.put(ruleId, enabled);
    }

    /**
     * 尝试匹配消息，返回回复内容（如果匹配）。
     *
     * @param message 入站消息文本
     * @param channelId 通道 ID
     * @param userId 用户 ID
     * @return 回复内容，如果没有匹配则返回 Optional.empty()
     */
    public Optional<String> match(String message, String channelId, String userId) {
        // 按 matchScore 降序排列
        var sortedRules = rules.stream()
            .filter(r -> ruleStates.getOrDefault(r.id(), false))
            .filter(r -> r.channelId() == null || r.channelId().equals(channelId))
            .sorted(Comparator.comparingDouble(AutoReplyRule::matchScore).reversed())
            .toList();

        for (var rule : sortedRules) {
            if (matches(rule, message)) {
                var reply = applyTemplate(rule.reply(), message, channelId, userId);
                log.info("Auto-reply matched: {} → {}", rule.name(), reply.substring(0, Math.min(50, reply.length())));
                return Optional.of(reply);
            }
        }
        return Optional.empty();
    }

    /**
     * 列出所有规则。
     */
    public List<AutoReplyRule> listRules() {
        return List.copyOf(rules);
    }

    /**
     * 获取规则。
     */
    public Optional<AutoReplyRule> getRule(String ruleId) {
        return rules.stream().filter(r -> r.id().equals(ruleId)).findFirst();
    }

    // ---- matching ----

    private boolean matches(AutoReplyRule rule, String message) {
        try {
            return switch (rule.type()) {
                case KEYWORD -> message.toLowerCase().contains(rule.pattern().toLowerCase());
                case REGEX -> Pattern.compile(rule.pattern(), Pattern.CASE_INSENSITIVE).matcher(message).find();
                case COMMAND -> {
                    var cmd = rule.pattern().startsWith("/") ? rule.pattern() : "/" + rule.pattern();
                    yield message.trim().toLowerCase().startsWith(cmd.toLowerCase());
                }
                case CONDITION -> evaluateCondition(rule.pattern(), message);
            };
        } catch (Exception e) {
            log.warn("Rule match failed for {}: {}", rule.id(), e.getMessage());
            return false;
        }
    }

    /**
     * 条件表达式评估（简化版）。
     * 支持的格式：
     * - "time=09:00-18:00" → 工作时间
     * - "length>100" → 消息长度大于 100
     * - "weekday=true" → 仅工作日
     */
    private boolean evaluateCondition(String condition, String message) {
        var parts = condition.split("=");
        if (parts.length == 2) {
            var key = parts[0].trim();
            var value = parts[1].trim();

            return switch (key.toLowerCase()) {
                case "time" -> {
                    // 格式: 09:00-18:00
                    var range = value.split("-");
                    if (range.length == 2) {
                        var now = LocalTime.now();
                        var start = LocalTime.parse(range[0].trim());
                        var end = LocalTime.parse(range[1].trim());
                        yield !now.isBefore(start) && !now.isAfter(end);
                    }
                    yield false;
                }
                case "weekday" -> {
                    var day = java.time.DayOfWeek.from(java.time.LocalDate.now());
                    var isWeekday = day != java.time.DayOfWeek.SATURDAY && day != java.time.DayOfWeek.SUNDAY;
                    yield "true".equalsIgnoreCase(value) == isWeekday;
                }
                default -> false;
            };
        }
        // length>100 等比较
        if (condition.contains(">")) {
            var idx = condition.indexOf('>');
            var key = condition.substring(0, idx).trim();
            var val = Integer.parseInt(condition.substring(idx + 1).trim());
            if ("length".equalsIgnoreCase(key)) return message.length() > val;
        }
        if (condition.contains("<")) {
            var idx = condition.indexOf('<');
            var key = condition.substring(0, idx).trim();
            var val = Integer.parseInt(condition.substring(idx + 1).trim());
            if ("length".equalsIgnoreCase(key)) return message.length() < val;
        }
        return false;
    }

    // ---- templates ----

    private String applyTemplate(String template, String message, String channelId, String userId) {
        return template
            .replace("{message}", message)
            .replace("{user}", userId != null ? userId : "用户")
            .replace("{channel}", channelId != null ? channelId : "web")
            .replace("{time}", LocalTime.now().toString().substring(0, 5))
            .replace("{date}", java.time.LocalDate.now().toString())
            .replace("{datetime}", java.time.LocalDateTime.now().toString().substring(0, 19));
    }
}
