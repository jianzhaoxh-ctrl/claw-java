package com.openclaw.desktop.trajectory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/**
 * 轨迹写入器 — 将 TrajectoryEvent 写入 JSONL 文件。
 * 对应 OpenClaw 的 TrajectoryWriter。
 *
 * <p>每行一个 JSON 对象，格式：
 * <pre>{"type":"AgentStarted","agentId":"default","modelId":"gpt-4o","timestamp":"2026-06-30T10:35:00Z"}</pre>
 */
public class TrajectoryWriter {

    private static final Logger log = LoggerFactory.getLogger(TrajectoryWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Path trajectoryFile;
    private BufferedWriter writer;
    private boolean closed = false;

    public TrajectoryWriter(Path trajectoryFile) throws IOException {
        this.trajectoryFile = trajectoryFile;
        Files.createDirectories(trajectoryFile.getParent());
        this.writer = Files.newBufferedWriter(trajectoryFile,
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        log.info("Trajectory writer opened: {}", trajectoryFile);
    }

    /**
     * 写入一个轨迹事件。
     */
    public void write(TrajectoryEvent event) {
        if (closed) {
            log.warn("Trajectory writer is closed, ignoring event: {}", event);
            return;
        }
        try {
            var json = MAPPER.writeValueAsString(event);
            writer.write(json);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write trajectory event: {}", e.getMessage());
        }
    }

    /**
     * 关闭写入器。
     */
    public void close() {
        if (closed) return;
        closed = true;
        try {
            writer.close();
            log.info("Trajectory writer closed: {}", trajectoryFile);
        } catch (IOException e) {
            log.error("Failed to close trajectory writer: {}", e.getMessage());
        }
    }

    /**
     * 获取轨迹文件路径。
     */
    public Path filePath() { return trajectoryFile; }

    /**
     * 是否已关闭。
     */
    public boolean isClosed() { return closed; }
}
