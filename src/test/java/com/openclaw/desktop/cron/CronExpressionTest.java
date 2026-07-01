package com.openclaw.desktop.cron;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cron 表达式解析器测试 — 覆盖 5/6/7 位语法、特殊字符、nextTime 计算。
 */
class CronExpressionTest {

    @Test
    @DisplayName("6-field every minute matches second=0")
    void testEveryMinute() {
        var cron = CronExpression.parse("0 * * * * ?");
        var t = ZonedDateTime.of(2026, 6, 28, 10, 30, 0, 0, ZoneId.systemDefault());
        assertTrue(cron.matches(t));
        var withSeconds = t.withSecond(30);
        assertFalse(cron.matches(withSeconds));
    }

    @Test
    @DisplayName("5-field expression is compatible (prepends 0 seconds)")
    void test5FieldCompatibility() {
        var cron = CronExpression.parse("* * * * *");
        var t = ZonedDateTime.of(2026, 6, 28, 10, 30, 0, 0, ZoneId.systemDefault());
        assertTrue(cron.matches(t));
    }

    @Test
    @DisplayName("every 15 minutes matches 0/15/30/45 only")
    void testEvery15Minutes() {
        var cron = CronExpression.parse("0 0/15 * * * ?");
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 0, 0, 0, zone)));
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 15, 0, 0, zone)));
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 30, 0, 0, zone)));
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 45, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 7, 0, 0, zone)));
    }

    @Test
    @DisplayName("daily at 9am matches only 09:00:00")
    void testDaily9am() {
        var cron = CronExpression.parse("0 0 9 * * ?");
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 9, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 9, 30, 0, 0, zone)));
    }

    @Test
    @DisplayName("weekdays MON-FRI excludes weekends")
    void testWeekdays() {
        var cron = CronExpression.parse("0 0 9 ? * MON-FRI");
        var zone = ZoneId.systemDefault();
        // 2024-01-01 是周一, 2023-12-31 是周日
        var monday = ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, zone);
        var sunday = ZonedDateTime.of(2023, 12, 31, 9, 0, 0, 0, zone);
        assertTrue(cron.matches(monday));
        assertFalse(cron.matches(sunday));
    }

    @Test
    @DisplayName("7-field with year restricts to specified year")
    void testWithYear() {
        var cron = CronExpression.parse("0 0 0 1 1 ? 2027");
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2027, 1, 1, 0, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 1, 1, 0, 0, 0, 0, zone)));
    }

    @Test
    @DisplayName("month and day names are resolved")
    void testNames() {
        var cron = CronExpression.parse("0 0 12 1 JAN ?");
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2026, 1, 1, 12, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 2, 1, 12, 0, 0, 0, zone)));
    }

    @Test
    @DisplayName("nextTimeAfter returns next matching instant")
    void testNextTimeAfter() {
        var cron = CronExpression.parse("0 0 9 * * ?");
        // 周日 2023-12-31 10:00 → 下一次 9 点是周一 2024-01-01 09:00
        var from = ZonedDateTime.of(2023, 12, 31, 10, 0, 0, 0, ZoneId.systemDefault()).toInstant();
        var next = cron.nextTimeAfter(from, ZoneId.systemDefault());
        assertNotNull(next);
        assertTrue(next.isAfter(from));
        var nextZdt = ZonedDateTime.ofInstant(next, ZoneId.systemDefault());
        assertEquals(9, nextZdt.getHour());
        assertEquals(0, nextZdt.getMinute());
    }

    @Test
    @DisplayName("nextTimeAfter for every minute is within 60 seconds")
    void testNextMinute() {
        var cron = CronExpression.parse("0 * * * * ?");
        var from = Instant.parse("2026-06-28T10:30:30Z");
        var next = cron.nextTimeAfter(from, ZoneId.systemDefault());
        assertNotNull(next);
        var diff = java.time.Duration.between(from, next).getSeconds();
        assertTrue(diff > 0 && diff <= 60, "next run should be within 60s, got " + diff);
    }

    @Test
    @DisplayName("comma list of minutes")
    void testCommaList() {
        var cron = CronExpression.parse("0 0,30 * * * ?");
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 0, 0, 0, zone)));
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 30, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 10, 15, 0, 0, zone)));
    }

    @Test
    @DisplayName("range expression in hours")
    void testRange() {
        var cron = CronExpression.parse("0 0 9-17 * * ?"); // 9-17点整点
        var zone = ZoneId.systemDefault();
        assertTrue(cron.matches(ZonedDateTime.of(2026, 6, 28, 12, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 8, 0, 0, 0, zone)));
        assertFalse(cron.matches(ZonedDateTime.of(2026, 6, 28, 18, 0, 0, 0, zone)));
    }
}
