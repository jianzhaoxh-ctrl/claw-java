package com.openclaw.desktop.ui.session;

import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.TranscriptEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 会话导出器 — 将会话导出为 Markdown / JSON / HTML 格式。
 * 对应 OpenClaw 的 export-trajectory 功能。
 */
public class SessionExporter {

    private static final Logger log = LoggerFactory.getLogger(SessionExporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /** 导出格式 */
    public enum Format {
        MARKDOWN, JSON, HTML
    }

    /** 导出会话到指定格式（自动生成文件名） */
    public Path export(Session session, Format format, Path outputDir) throws IOException {
        if (!Files.exists(outputDir)) Files.createDirectories(outputDir);
        var timestamp = FORMATTER.format(Instant.now()).replace(":", "-");
        var filename = "session-" + session.key().toString().replace(":", "-") + "-" + timestamp;
        var path = switch (format) {
            case MARKDOWN -> outputDir.resolve(filename + ".md");
            case JSON -> outputDir.resolve(filename + ".json");
            case HTML -> outputDir.resolve(filename + ".html");
        };
        return export(session, null, format, path);
    }

    /** 导出会话到指定文件路径（带标题，由 SessionListPanel 使用） */
    public Path export(Session session, String title, Format format, Path outputPath) throws IOException {
        if (!Files.exists(outputPath.getParent())) Files.createDirectories(outputPath.getParent());
        return switch (format) {
            case MARKDOWN -> exportMarkdown(session, outputPath);
            case JSON -> exportJson(session, outputPath);
            case HTML -> exportHtml(session, outputPath);
        };
    }

    /** 导出为 Markdown */
    private Path exportMarkdown(Session session, Path path) throws IOException {
        var sb = new StringBuilder();
        sb.append("# Session Export\n\n");
        sb.append("**Session Key**: ").append(session.key()).append("\n");
        sb.append("**Created**: ").append(FORMATTER.format(session.createdAt())).append("\n");
        sb.append("**Messages**: ").append(session.transcript().size()).append("\n\n");
        sb.append("---\n\n");

        for (var entry : session.transcript().entries()) {
            sb.append("### ").append(switch (entry.role()) {
                case "user" -> "👤 User";
                case "assistant" -> "🤖 Assistant";
                case "tool" -> "🔧 Tool";
                case "system" -> "📋 System";
                default -> entry.role();
            }).append("\n\n");

            sb.append(entry.content()).append("\n\n");

            if (entry.timestamp() != null) {
                sb.append("> *").append(FORMATTER.format(entry.timestamp())).append("*\n\n");
            }
        }

        sb.append("---\n\n*Exported by ClawDesktop at ").append(FORMATTER.format(Instant.now())).append("*\n");
        Files.writeString(path, sb.toString());
        log.info("Exported session as Markdown: {}", path);
        return path;
    }

    /** 导出为 JSON */
    private Path exportJson(Session session, Path path) throws IOException {
        var data = Map.of(
            "sessionKey", session.key().toString(),
            "createdAt", FORMATTER.format(session.createdAt()),
            "messageCount", String.valueOf(session.transcript().size()),
            "messages", session.transcript().entries().stream()
                .map(e -> Map.of(
                    "role", e.role(),
                    "content", e.content(),
                    "timestamp", e.timestamp() != null ? FORMATTER.format(e.timestamp()) : "",
                    "toolCallId", e.toolCallId() != null ? e.toolCallId() : ""
                ))
                .toList()
        );
        MAPPER.writeValue(path.toFile(), data);
        log.info("Exported session as JSON: {}", path);
        return path;
    }

    /** 导出为 HTML */
    private Path exportHtml(Session session, Path path) throws IOException {
        var sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html><head>\n");
        sb.append("<meta charset='UTF-8'>\n");
        sb.append("<title>Session Export - ").append(session.key()).append("</title>\n");
        sb.append("<style>\n");
        sb.append("body { font-family: -apple-system, sans-serif; max-width: 800px; margin: 20px auto; background: #1e1e2e; color: #cdd6f4; }\n");
        sb.append(".user-msg { background: #313244; padding: 12px; border-radius: 8px; margin: 8px 0; text-align: right; }\n");
        sb.append(".assistant-msg { background: #45475a; padding: 12px; border-radius: 8px; margin: 8px 0; }\n");
        sb.append(".tool-msg { background: #585b70; padding: 8px; border-radius: 4px; font-family: monospace; }\n");
        sb.append("pre { background: #11111b; padding: 12px; border-radius: 6px; overflow-x: auto; }\n");
        sb.append("code { font-family: Consolas, monospace; }\n");
        sb.append("a { color: #89b4fa; }\n");
        sb.append("</style>\n</head><body>\n");

        sb.append("<h1>Session: ").append(session.key()).append("</h1>\n");
        sb.append("<p>Created: ").append(FORMATTER.format(session.createdAt())).append("</p>\n");

        for (var entry : session.transcript().entries()) {
            var cssClass = switch (entry.role()) {
                case "user" -> "user-msg";
                case "assistant" -> "assistant-msg";
                case "tool" -> "tool-msg";
                default -> "assistant-msg";
            };
            sb.append("<div class='").append(cssClass).append("'>\n");
            sb.append("<strong>").append(entry.role()).append("</strong><br>\n");
            sb.append(escapeHtml(entry.content())).append("\n");
            sb.append("</div>\n");
        }

        sb.append("<hr><p><small>Exported by ClawDesktop</small></p>\n");
        sb.append("</body></html>");
        Files.writeString(path, sb.toString());
        log.info("Exported session as HTML: {}", path);
        return path;
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
