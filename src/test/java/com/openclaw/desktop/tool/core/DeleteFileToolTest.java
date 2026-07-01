package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.ToolInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DeleteFileTool 单元测试。
 */
class DeleteFileToolTest {

    @TempDir
    Path tempDir;

    private final DeleteFileTool tool = new DeleteFileTool();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String json(Path path) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        return node.toString();
    }

    @Test
    @DisplayName("descriptor has correct name")
    void testDescriptor() {
        var d = tool.descriptor();
        assertEquals("delete_file", d.name());
    }

    @Test
    @DisplayName("delete existing file succeeds")
    void testDeleteFile() throws Exception {
        var filePath = tempDir.resolve("to-delete.txt");
        Files.writeString(filePath, "content");
        var input = new ToolInput("c1", json(filePath));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertTrue(result.content().contains("to-delete.txt"));
    }

    @Test
    @DisplayName("delete non-existent file fails")
    void testDeleteNonExistent() {
        var input = new ToolInput("c2", json(tempDir.resolve("nope.txt")));
        var result = tool.execute(input, null).block();
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("delete directory with contents")
    void testDeleteDirectory() throws Exception {
        var dirPath = tempDir.resolve("subdir");
        Files.createDirectories(dirPath);
        Files.writeString(dirPath.resolve("a.txt"), "a");
        Files.writeString(dirPath.resolve("b.txt"), "b");
        var input = new ToolInput("c3", json(dirPath));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
    }

    @Test
    @DisplayName("delete empty directory")
    void testDeleteEmptyDir() throws Exception {
        var dirPath = tempDir.resolve("emptydir");
        Files.createDirectories(dirPath);
        var input = new ToolInput("c4", json(dirPath));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
    }
}
