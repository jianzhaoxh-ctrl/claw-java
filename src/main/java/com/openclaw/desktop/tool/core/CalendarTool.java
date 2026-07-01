package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.*;
import com.openclaw.desktop.types.JsonObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 日历工具 — 查看日历、创建事件（通过 iCal URL / CalDAV / Google Calendar API）。
 * 对应 OpenClaw 的 calendar plugin。
 * 支持：查看日期、查看近期日程、创建事件（ICS 格式导出）。
 */
public class CalendarTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(CalendarTool.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String calendarUrl; // iCal / CalDAV URL
    private final String apiKey;

    public CalendarTool() {
        this(null, null);
    }

    public CalendarTool(String calendarUrl, String apiKey) {
        this.calendarUrl = calendarUrl;
        this.apiKey = apiKey;
    }

    @Override
    public ToolDescriptor descriptor() {
        var inputSchema = new JsonObject(Map.of(
            "action", Map.of("type", "string", "enum", "today,week,create,list",
                "description", "Calendar action"),
            "date", Map.of("type", "string", "description", "Date in YYYY-MM-DD format (for specific date)"),
            "title", Map.of("type", "string", "description", "Event title (for create)"),
            "start", Map.of("type", "string", "description", "Start time ISO format (for create)"),
            "end", Map.of("type", "string", "description", "End time ISO format (for create)"),
            "location", Map.of("type", "string", "description", "Event location (for create)"),
            "description", Map.of("type", "string", "description", "Event description (for create)")
        ));
        return new ToolDescriptor(
            "calendar",
            "Calendar",
            "View calendar and manage events. Actions: today, week, create, list. Supports iCal/CalDAV integration.",
            inputSchema,
            JsonObject.empty()
        );
    }

    @Override
    public Mono<ToolResult> execute(ToolInput input, ToolContext context) {
        return Mono.fromCallable(() -> {
            var args = MAPPER.readTree(input.arguments());
            var action = args.path("action").asText("today");

            return switch (action) {
                case "today" -> getToday(input);
                case "week" -> getWeek(input);
                case "create" -> createEvent(args, input);
                case "list" -> listEvents(args, input);
                default -> ToolResult.failure(input.toolCallId(), "Unknown action: " + action);
            };
        });
    }

    private ToolResult getToday(ToolInput input) {
        var today = LocalDate.now();
        var dayOfWeek = today.getDayOfWeek().toString();
        var formatted = today.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"));

        var sb = new StringBuilder();
        sb.append("📅 ").append(formatted).append(" (").append(dayOfWeek).append(")\n\n");

        // 如果配置了日历 URL，尝试获取事件
        if (calendarUrl != null && !calendarUrl.isEmpty()) {
            sb.append(fetchCalendarEvents(today.toString()));
        } else {
            sb.append("⚠️ 未配置日历源。请在设置中配置 iCal URL 或 CalDAV 地址。\n");
            sb.append("你仍然可以使用 `calendar create` 创建事件（导出为 ICS 格式）。\n");
        }

        return ToolResult.success(input.toolCallId(), sb.toString().trim());
    }

    private ToolResult getWeek(ToolInput input) {
        var today = LocalDate.now();
        var sb = new StringBuilder();
        sb.append("📅 本周日程 (").append(today).append(" ~ +7天)\n\n");

        for (int i = 0; i < 7; i++) {
            var date = today.plusDays(i);
            var dow = date.getDayOfWeek().toString();
            sb.append(date.format(DateTimeFormatter.ofPattern("MM-dd"))).append(" (").append(dow, 0, 3).append(")\n");
        }

        if (calendarUrl == null || calendarUrl.isEmpty()) {
            sb.append("\n⚠️ 未配置日历源。");
        }

        return ToolResult.success(input.toolCallId(), sb.toString().trim());
    }

    private ToolResult createEvent(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        var title = args.path("title").asText("");
        var start = args.path("start").asText("");
        var end = args.path("end").asText("");
        var location = args.path("location").asText("");
        var desc = args.path("description").asText("");

        if (title.isEmpty() || start.isEmpty()) {
            return ToolResult.failure(input.toolCallId(), "title and start are required for creating events");
        }

        // 生成 ICS 格式事件
        var ics = new StringBuilder();
        ics.append("BEGIN:VCALENDAR\nVERSION:2.0\nPRODID:-//ClawDesktop//Calendar//CN\n");
        ics.append("BEGIN:VEVENT\n");
        ics.append("UID:claw-").append(System.currentTimeMillis()).append("@clawdesktop\n");
        ics.append("DTSTAMP:").append(formatIcsDate(java.time.Instant.now().toString())).append("\n");
        ics.append("DTSTART:").append(formatIcsDate(start)).append("\n");
        if (!end.isEmpty()) {
            ics.append("DTEND:").append(formatIcsDate(end)).append("\n");
        }
        ics.append("SUMMARY:").append(escapeIcs(title)).append("\n");
        if (!location.isEmpty()) {
            ics.append("LOCATION:").append(escapeIcs(location)).append("\n");
        }
        if (!desc.isEmpty()) {
            ics.append("DESCRIPTION:").append(escapeIcs(desc)).append("\n");
        }
        ics.append("END:VEVENT\nEND:VCALENDAR\n");

        return ToolResult.success(input.toolCallId(),
            "✅ 事件已创建（ICS 格式）:\n\n```ics\n" + ics + "```\n\n" +
            "将以上内容保存为 .ics 文件即可导入到日历应用。");
    }

    private ToolResult listEvents(com.fasterxml.jackson.databind.JsonNode args, ToolInput input) {
        if (calendarUrl == null || calendarUrl.isEmpty()) {
            return ToolResult.success(input.toolCallId(),
                "⚠️ 未配置日历源。配置 iCal URL 后可查看远程日历事件。\n" +
                "或使用 `calendar create` 创建本地事件。");
        }
        return ToolResult.success(input.toolCallId(), fetchCalendarEvents(""));
    }

    private String fetchCalendarEvents(String dateFilter) {
        try {
            var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
            var request = HttpRequest.newBuilder()
                .uri(URI.create(calendarUrl))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "❌ 日历获取失败: HTTP " + response.statusCode();
            }
            // 解析 iCal 中的 VEVENT
            var content = response.body();
            var events = new java.util.ArrayList<String>();
            var lines = content.split("\n");
            var inEvent = false;
            var summary = ""; var dtstart = ""; var location = "";
            for (var line : lines) {
                line = line.trim();
                if (line.equals("BEGIN:VEVENT")) { inEvent = true; summary = dtstart = location = ""; }
                else if (line.equals("END:VEVENT")) {
                    if (inEvent && !summary.isEmpty()) {
                        events.add("📍 " + dtstart + " | " + summary + (location.isEmpty() ? "" : " @ " + location));
                    }
                    inEvent = false;
                }
                else if (inEvent) {
                    if (line.startsWith("SUMMARY:")) summary = line.substring(8);
                    else if (line.startsWith("DTSTART")) dtstart = line.substring(line.indexOf(':') + 1);
                    else if (line.startsWith("LOCATION:")) location = line.substring(9);
                }
            }
            if (events.isEmpty()) return "今日无事件。";
            return String.join("\n", events);
        } catch (Exception e) {
            return "❌ 日历获取失败: " + e.getMessage();
        }
    }

    private String formatIcsDate(String isoDate) {
        // 简化：去掉非数字字符
        return isoDate.replaceAll("[-:]", "").replaceAll("\\..*$", "");
    }

    private String escapeIcs(String text) {
        return text.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\n", "\\n");
    }
}
