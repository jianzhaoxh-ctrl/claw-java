package com.openclaw.desktop.trajectory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TrajectoryWriter 单元测试。
 */
class TrajectoryWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void testWriteAndRead() throws Exception {
        var trajectoryFile = tempDir.resolve("test-trajectory.jsonl");
        var writer = new TrajectoryWriter(trajectoryFile);

        writer.write(new TrajectoryEvent.AgentStarted("default", "gpt-4o", Instant.now()));
        writer.write(new TrajectoryEvent.TurnStarted(0, Instant.now()));
        writer.write(new TrajectoryEvent.TurnEnded(0, "stop",
            new com.openclaw.desktop.llm.UsageInfo(10, 20, 30, null, null), Instant.now()));
        writer.write(new TrajectoryEvent.AgentEnded("default", "stop", Instant.now()));
        writer.close();

        assertTrue(trajectoryFile.toFile().exists(), "Trajectory file should exist");
        var content = java.nio.file.Files.readString(trajectoryFile);
        assertTrue(content.contains("AgentStarted"), "Should contain AgentStarted event");
        assertTrue(content.contains("TurnStarted"), "Should contain TurnStarted event");
    }

    @Test
    void testClose() throws Exception {
        var trajectoryFile = tempDir.resolve("close-test.jsonl");
        var writer = new TrajectoryWriter(trajectoryFile);
        assertFalse(writer.isClosed());
        writer.close();
        assertTrue(writer.isClosed());
    }
}
