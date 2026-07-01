package com.openclaw.desktop.trajectory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹导出器 — 从 JSONL 文件读取轨迹事件并导出。
 * 对应 OpenClaw 的 TrajectoryExporter。
 */
public class TrajectoryExporter {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryExporter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 从 JSONL 文件读取所有轨迹事件。
     */
    public List<TrajectoryEvent> loadFromFile(Path trajectoryFile) throws IOException {
        if (!Files.exists(trajectoryFile)) {
            return List.of();
        }

        var events = new ArrayList<TrajectoryEvent>();
        var lines = Files.readAllLines(trajectoryFile);
        for (var line : lines) {
            if (line.isBlank()) continue;
            try {
                var tree = MAPPER.readTree(line);
                var type = tree.path("type").asText("");
                var event = parseEvent(type, tree);
                if (event != null) events.add(event);
            } catch (Exception e) {
                log.warn("Failed to parse trajectory line: {}", e.getMessage());
            }
        }
        return events;
    }

    /**
     * 导出轨迹为可读的 Markdown 格式。
     */
    public String exportToMarkdown(List<TrajectoryEvent> events) {
        var sb = new StringBuilder();
        sb.append("# Agent Trajectory\n\n");
        sb.append("| 时间 | 事件 | 详情 |\n");
        sb.append("|------|------|------|\n");

        for (var event : events) {
            var ts = extractTimestamp(event);
            sb.append("| ").append(ts).append(" | ");
            sb.append(event.getClass().getSimpleName()).append(" | ");
            sb.append(formatEventDetail(event)).append(" |\n");
        }

        return sb.toString();
    }

    /**
     * 搜索轨迹事件。
     */
    public List<TrajectoryEvent> search(List<TrajectoryEvent> events, String keyword) {
        return events.stream()
            .filter(e -> formatEventDetail(e).toLowerCase().contains(keyword.toLowerCase()))
            .toList();
    }

    private TrajectoryEvent parseEvent(String type, com.fasterxml.jackson.databind.JsonNode tree) {
        try {
            return MAPPER.treeToValue(tree, TrajectoryEvent.class);
        } catch (Exception e) {
            log.warn("Failed to parse event type {}: {}", type, e.getMessage());
            return null;
        }
    }

    private String extractTimestamp(TrajectoryEvent event) {
        return switch (event) {
            case TrajectoryEvent.AgentStarted(var a, var m, var ts) -> ts.toString();
            case TrajectoryEvent.AgentEnded(var a, var sr, var ts) -> ts.toString();
            case TrajectoryEvent.TurnStarted(var i, var ts) -> ts.toString();
            case TrajectoryEvent.TurnEnded(var i, var sr, var u, var ts) -> ts.toString();
            case TrajectoryEvent.UserMessageInjected(var id, var cp, var ts) -> ts.toString();
            case TrajectoryEvent.LlmRequestSent(var p, var m, var c, var ts) -> ts.toString();
            case TrajectoryEvent.LlmResponseReceived(var p, var m, var sr, var t, var ts) -> ts.toString();
            case TrajectoryEvent.ToolCallStarted(var id, var n, var ap, var ts) -> ts.toString();
            case TrajectoryEvent.ToolCallEnded(var id, var n, var s, var rp, var ts) -> ts.toString();
            case TrajectoryEvent.ContextCompacted(var o, var c, var ts) -> ts.toString();
            case TrajectoryEvent.ErrorOccurred(var et, var em, var ts) -> ts.toString();
            case TrajectoryEvent.SteeringInjected(var src, var mc, var ts) -> ts.toString();
        };
    }

    private String formatEventDetail(TrajectoryEvent event) {
        return switch (event) {
            case TrajectoryEvent.AgentStarted(var a, var m, var ts) -> "agent=" + a + " model=" + m;
            case TrajectoryEvent.AgentEnded(var a, var sr, var ts) -> "agent=" + a + " reason=" + sr;
            case TrajectoryEvent.TurnStarted(var i, var ts) -> "turn=" + i;
            case TrajectoryEvent.TurnEnded(var i, var sr, var u, var ts) -> "turn=" + i + " reason=" + sr + " tokens=" + u.totalTokens();
            case TrajectoryEvent.UserMessageInjected(var id, var cp, var ts) -> "id=" + id + " preview=" + cp;
            case TrajectoryEvent.LlmRequestSent(var p, var m, var c, var ts) -> "provider=" + p + " model=" + m + " msgs=" + c;
            case TrajectoryEvent.LlmResponseReceived(var p, var m, var sr, var t, var ts) -> "provider=" + p + " model=" + m + " tokens=" + t;
            case TrajectoryEvent.ToolCallStarted(var id, var n, var ap, var ts) -> "tool=" + n + " id=" + id;
            case TrajectoryEvent.ToolCallEnded(var id, var n, var s, var rp, var ts) -> "tool=" + n + " success=" + s;
            case TrajectoryEvent.ContextCompacted(var o, var c, var ts) -> "original=" + o + " compacted=" + c;
            case TrajectoryEvent.ErrorOccurred(var et, var em, var ts) -> "type=" + et + " msg=" + em;
            case TrajectoryEvent.SteeringInjected(var src, var mc, var ts) -> "source=" + src + " count=" + mc;
        };
    }
}
