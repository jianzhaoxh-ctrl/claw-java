package com.openclaw.desktop.trajectory;

import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 轨迹回放器 — 从 JSONL 文件回放历史会话。
 * 对应 OpenClaw 的 TrajectoryReplayer。
 */
public class TrajectoryReplayer {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryReplayer.class);
    private final TrajectoryExporter exporter = new TrajectoryExporter();

    /**
     * 加载轨迹文件中的所有事件。
     */
    public List<TrajectoryEvent> load(Path trajectoryFile) throws IOException {
        return exporter.loadFromFile(trajectoryFile);
    }

    /**
     * 从轨迹事件重建对话消息列表。
     */
    public List<Message> buildConversation(List<TrajectoryEvent> events) {
        var messages = new ArrayList<Message>();
        for (var event : events) {
            switch (event) {
                case TrajectoryEvent.UserMessageInjected(var id, var preview, var ts) ->
                    messages.add(new Message.UserMessage(preview));
                case TrajectoryEvent.LlmResponseReceived(var prov, var model, var reason, var tokens, var ts) ->
                    messages.add(new Message.AssistantMessage(
                "LLM response (" + tokens + " tokens)",
                List.of()  // no tool calls in replay
            ));
                case TrajectoryEvent.ToolCallEnded(var id, var name, var success, var preview, var ts) ->
                    messages.add(new Message.ToolResultMessage(
                    id,
                    name,
                    List.of(new MessageContent.TextContent(preview)),
                    !success,
                    ts.toEpochMilli()
                ));
                case TrajectoryEvent.ContextCompacted(var orig, var comp, var ts) ->
                    messages.add(new Message.SystemMessage("Context compacted: " + orig + " → " + comp + " tokens"));
                case TrajectoryEvent.SteeringInjected(var src, var count, var ts) ->
                    messages.add(new Message.SystemMessage("Steering messages from " + src + " (" + count + " injected)"));
                default -> {} // 其他事件不影响消息列表
            }
        }
        return messages;
    }

    /**
     * 搜索轨迹事件中的关键词。
     */
    public List<TrajectoryEvent> search(List<TrajectoryEvent> events, String keyword) {
        return exporter.search(events, keyword);
    }
}
