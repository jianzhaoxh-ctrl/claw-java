package com.openclaw.desktop.cron;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;

/**
 * Cron 表达式解析器 — Quartz 风格，支持 6-7 位表达式。
 *
 * <p>格式：{@code 秒 分 时 日 月 周 [年]}
 * <ul>
 *   <li>6 位：{@code 秒 分 时 日 月 周}</li>
 *   <li>7 位：{@code 秒 分 时 日 月 周 年}</li>
 *   <li>5 位（兼容旧格式）：自动补秒位 0，即 {@code 0 分 时 日 月 周}</li>
 * </ul>
 *
 * <p>支持的语法：
 * <ul>
 *   <li>{@code *} 任意值</li>
 *   <li>{@code ?} 不指定（仅日/周字段，与另一字段构成 OR 语义）</li>
 *   <li>{@code ,} 列表，如 {@code 1,5,10}</li>
 *   <li>{@code -} 范围，如 {@code 1-5}</li>
 *   <li>{@code /} 步长，如 {@code 0/15}（从0开始每15）、{@code * /5}</li>
 *   <li>{@code L} 最后（日：本月最后一天；周：如 {@code 6L} 本月最后一个周五）</li>
 *   <li>{@code W} 最近工作日（日：如 {@code 15W}）</li>
 *   <li>{@code #} 第 N 个（周：如 {@code 6#3} 本月第3个周五）</li>
 *   <li>月名：JAN-DEC；周名：SUN-SAT</li>
 * </ul>
 *
 * <p>示例：
 * <ul>
 *   <li>{@code 0 * * * * ?} 每分钟</li>
 *   <li>{@code 0 0/15 * * * ?} 每15分钟</li>
 *   <li>{@code 0 0 9 ? * MON-FRI} 每个工作日9点</li>
 *   <li>{@code 0 0 12 1 * ?} 每月1号12点</li>
 *   <li>{@code 0 30 10 ? * 6L} 每月最后一个周五10:30</li>
 * </ul>
 */
public final class CronExpression {

    private static final Logger log = LoggerFactory.getLogger(CronExpression.class);

    private final String expression;
    private final BitSet seconds = new BitSet(60);
    private final BitSet minutes = new BitSet(60);
    private final BitSet hours = new BitSet(24);
    private final BitSet daysOfMonth = new BitSet(32);   // 1-31
    private final BitSet months = new BitSet(13);         // 1-12
    private final BitSet daysOfWeek = new BitSet(8);      // 0-7 (0和7都是周日)
    private final BitSet years;                           // null = 任意年

    // 特殊标记
    private boolean lastDayOfMonth = false;   // 日 = L
    private int lastWeekdayOfMonth = -1;      // 日 = LW (最后一个工作日)
    private int nearestWeekday = -1;          // 日 = nW
    private int lastDayOfWeek = -1;           // 周 = nL
    private final Map<Integer, Integer> nthDayOfWeek = new HashMap<>(); // 周 = n#k

    private boolean dayOfMonthQuestion; // 日为 ?（不指定，仅看周）
    private boolean dayOfWeekQuestion;  // 周为 ?（不指定，仅看日）

    private CronExpression(String expr, boolean hasYear) {
        this.expression = expr;
        this.years = hasYear ? new BitSet(2100) : null;
    }

    /**
     * 解析 cron 表达式。
     */
    public static CronExpression parse(String expr) {
        Objects.requireNonNull(expr, "cron expression");
        var trimmed = expr.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("Empty cron expression");

        var parts = trimmed.split("\\s+");
        boolean hasYear;
        if (parts.length == 7) {
            hasYear = true;
        } else if (parts.length == 6) {
            hasYear = false;
        } else if (parts.length == 5) {
            // 5 段兼容：前面补 "0" 作为秒
            parts = new String[]{"0", parts[0], parts[1], parts[2], parts[3], parts[4]};
            hasYear = false;
        } else {
            throw new IllegalArgumentException(
                "Invalid cron expression (expected 5/6/7 fields): " + expr);
        }

        var cron = new CronExpression(expr, hasYear);
        cron.parseField(parts[0], cron.seconds, 0, 59, "seconds");
        cron.parseField(parts[1], cron.minutes, 0, 59, "minutes");
        cron.parseField(parts[2], cron.hours, 0, 23, "hours");
        cron.parseDayOfMonth(parts[3]);
        cron.parseField(parts[4], cron.months, 1, 12, "months", MONTH_NAMES);
        cron.parseDayOfWeek(parts[5]);
        if (hasYear) {
            cron.parseField(parts[6], cron.years, 1970, 2099, "years");
        }
        return cron;
    }

    public String expression() { return expression; }

    // ---- 字段解析 ----

    private static final Map<String, Integer> MONTH_NAMES = Map.ofEntries(
        Map.entry("JAN", 1), Map.entry("FEB", 2), Map.entry("MAR", 3), Map.entry("APR", 4),
        Map.entry("MAY", 5), Map.entry("JUN", 6), Map.entry("JUL", 7), Map.entry("AUG", 8),
        Map.entry("SEP", 9), Map.entry("OCT", 10), Map.entry("NOV", 11), Map.entry("DEC", 12)
    );
    private static final Map<String, Integer> DAY_NAMES = Map.of(
        "SUN", 0, "MON", 1, "TUE", 2, "WED", 3, "THU", 4, "FRI", 5, "SAT", 6
    );

    private void parseField(String field, BitSet bits, int min, int max, String name) {
        parseField(field, bits, min, max, name, null);
    }

    private void parseField(String field, BitSet bits, int min, int max, String name,
                            Map<String, Integer> names) {
        if (field.equals("*") || field.equals("?")) {
            setRange(bits, min, max, 1);
            return;
        }
        for (var part : field.split(",")) {
            parsePart(part.trim(), bits, min, max, name, names);
        }
    }

    private void parsePart(String part, BitSet bits, int min, int max, String name,
                           Map<String, Integer> names) {
        // 处理步长 a/b 或 */b
        if (part.contains("/")) {
            var sp = part.split("/", 2);
            var range = sp[0].trim();
            var step = Integer.parseInt(sp[1].trim());
            int start, end;
            if (range.equals("*")) { start = min; end = max; }
            else if (range.contains("-")) {
                var rp = range.split("-", 2);
                start = resolve(rp[0].trim(), names);
                end = resolve(rp[1].trim(), names);
            } else {
                start = resolve(range, names);
                end = max;
            }
            for (int v = start; v <= end; v += step) {
                if (v >= min && v <= max) bits.set(v);
            }
            return;
        }
        // 范围 a-b
        if (part.contains("-")) {
            var rp = part.split("-", 2);
            var start = resolve(rp[0].trim(), names);
            var end = resolve(rp[1].trim(), names);
            setRange(bits, start, end, 1);
            return;
        }
        // 单值
        var v = resolve(part, names);
        if (v < min || v > max) {
            throw new IllegalArgumentException(name + " value out of range: " + v);
        }
        bits.set(v);
    }

    private int resolve(String s, Map<String, Integer> names) {
        if (names != null) {
            var upper = s.toUpperCase();
            if (names.containsKey(upper)) return names.get(upper);
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid value: " + s);
        }
    }

    private void setRange(BitSet bits, int start, int end, int step) {
        for (int v = start; v <= end; v += step) bits.set(v);
    }

    private void parseDayOfMonth(String field) {
        if (field.equals("?")) {
            dayOfMonthQuestion = true;
            return;
        }
        if (field.equals("*")) {
            setRange(daysOfMonth, 1, 31, 1);
            return;
        }
        if (field.equalsIgnoreCase("L")) {
            lastDayOfMonth = true;
            return;
        }
        if (field.equalsIgnoreCase("LW")) {
            lastWeekdayOfMonth = 1; // 标记
            return;
        }
        // nW
        if (field.toUpperCase().endsWith("W")) {
            var num = field.substring(0, field.length() - 1);
            nearestWeekday = Integer.parseInt(num);
            return;
        }
        parseField(field, daysOfMonth, 1, 31, "dayOfMonth");
    }

    private void parseDayOfWeek(String field) {
        if (field.equals("?")) {
            dayOfWeekQuestion = true;
            return;
        }
        if (field.equals("*")) {
            setRange(daysOfWeek, 0, 7, 1);
            return;
        }
        // nL (最后一个周n)
        if (field.toUpperCase().endsWith("L")) {
            var num = field.substring(0, field.length() - 1);
            lastDayOfWeek = resolve(num, DAY_NAMES);
            return;
        }
        // n#k (第k个周n)
        if (field.contains("#")) {
            var sp = field.split("#", 2);
            var day = resolve(sp[0].trim(), DAY_NAMES);
            var nth = Integer.parseInt(sp[1].trim());
            nthDayOfWeek.put(day, nth);
            return;
        }
        parseField(field, daysOfWeek, 0, 7, "dayOfWeek", DAY_NAMES);
        // 7 归一为 0（周日）
        if (daysOfWeek.get(7)) {
            daysOfWeek.set(0);
            daysOfWeek.clear(7);
        }
    }

    // ---- 匹配 ----

    /** 检查指定时间是否匹配。 */
    public boolean matches(ZonedDateTime time) {
        if (years != null && !years.get(time.getYear())) return false;
        if (!seconds.get(time.getSecond())) return false;
        if (!minutes.get(time.getMinute())) return false;
        if (!hours.get(time.getHour())) return false;
        if (!months.get(time.getMonthValue())) return false;
        return matchesDay(time);
    }

    private boolean matchesDay(ZonedDateTime time) {
        // Quartz 语义：日和周不能同时指定，一个必须是 ?（不指定）
        if (dayOfMonthQuestion && dayOfWeekQuestion) return true;
        if (dayOfMonthQuestion) return dayOfWeekMatches(time);
        if (dayOfWeekQuestion) return dayOfMonthMatches(time);
        // 两者都指定时 OR（兼容非标准用法）
        return dayOfMonthMatches(time) || dayOfWeekMatches(time);
    }

    private boolean dayOfMonthMatches(ZonedDateTime time) {
        if (lastDayOfMonth) {
            return time.getDayOfMonth() == time.toLocalDate().lengthOfMonth();
        }
        if (lastWeekdayOfMonth > 0) {
            // 本月最后一个工作日
            var lastDay = time.toLocalDate().lengthOfMonth();
            var d = time.withDayOfMonth(lastDay);
            while (d.getDayOfWeek().getValue() > 5) d = d.minusDays(1);
            return time.getDayOfMonth() == d.getDayOfMonth();
        }
        if (nearestWeekday > 0) {
            // 离 nearestWeekday 最近的工作日
            var d = time.withDayOfMonth(Math.min(nearestWeekday, time.toLocalDate().lengthOfMonth()));
            while (d.getDayOfWeek().getValue() > 5) {
                d = d.getDayOfWeek() == DayOfWeek.SATURDAY ? d.minusDays(1) : d.plusDays(1);
            }
            return time.getDayOfMonth() == d.getDayOfMonth();
        }
        return daysOfMonth.get(time.getDayOfMonth());
    }

    private boolean dayOfWeekMatches(ZonedDateTime time) {
        if (lastDayOfWeek >= 0) {
            // 本月最后一个 lastDayOfWeek
            if (toBitValue(time.getDayOfWeek()) != lastDayOfWeek) return false;
            var last = time.toLocalDate().lengthOfMonth();
            var d = time.withDayOfMonth(last);
            while (toBitValue(d.getDayOfWeek()) != lastDayOfWeek) d = d.minusDays(1);
            return time.getDayOfMonth() == d.getDayOfMonth();
        }
        if (!nthDayOfWeek.isEmpty()) {
            for (var entry : nthDayOfWeek.entrySet()) {
                var day = entry.getKey();
                var nth = entry.getValue();
                if (toBitValue(time.getDayOfWeek()) != day) continue;
                // 是否第 nth 个
                var weekOfMonth = (time.getDayOfMonth() - 1) / 7 + 1;
                if (weekOfMonth == nth) return true;
            }
            return false;
        }
        return daysOfWeek.get(toBitValue(time.getDayOfWeek()));
    }

    /** Java DayOfWeek 转 cron 位值（周日=0）。 */
    private static int toBitValue(DayOfWeek dow) {
        return dow.getValue() % 7; // Sunday(7)→0, Monday(1)→1, ...
    }

    // ---- 计算下一次匹配时间 ----

    /**
     * 计算 {@code after} 之后的下一个匹配时间。
     * @param after 起始时间（不含）
     * @param zone 时区
     * @return 下一次匹配时间，若无匹配（如年份超出范围）返回 null
     */
    public Instant nextTimeAfter(Instant after, ZoneId zone) {
        var time = ZonedDateTime.ofInstant(after, zone).plusSeconds(1)
            .withNano(0);
        return nextTime(time, zone);
    }

    private Instant nextTime(ZonedDateTime start, ZoneId zone) {
        var time = start;
        int maxIterations = 366 * 24 * 60; // 最多遍历约一年的分钟级跳转
        var lastYear = -1;
        for (int i = 0; i < maxIterations * 60; i++) {
            if (years != null && !years.get(time.getYear())) {
                // 跳到下一年1月1日0时0分0秒
                time = time.withYear(time.getYear() + 1).withMonth(1).withDayOfMonth(1)
                    .withHour(0).withMinute(0).withSecond(0);
                if (time.getYear() > 2099) return null;
                continue;
            }
            if (!months.get(time.getMonthValue())) {
                time = time.plusMonths(1).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!matchesDay(time)) {
                time = time.plusDays(1).withHour(0).withMinute(0).withSecond(0);
                continue;
            }
            if (!hours.get(time.getHour())) {
                time = time.plusHours(1).withMinute(0).withSecond(0);
                continue;
            }
            if (!minutes.get(time.getMinute())) {
                time = time.plusMinutes(1).withSecond(0);
                continue;
            }
            if (!seconds.get(time.getSecond())) {
                time = time.plusSeconds(1);
                continue;
            }
            return time.toInstant();
        }
        log.warn("Cron expression '{}' found no match within iteration limit", expression);
        return null;
    }

    @Override
    public String toString() {
        return "Cron[" + expression + "]";
    }
}
