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
 * EditFileTool 单元测试。
 */
class EditFileToolTest {

    @TempDir
    Path tempDir;

    private final EditFileTool tool = new EditFileTool();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String json(Path path, String oldText, String newText) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        node.put("oldText", oldText);
        node.put("newText", newText);
        return node.toString();
    }

    @Test
    @DisplayName("descriptor has correct name")
    void testDescriptor() {
        var d = tool.descriptor();
        assertEquals("edit_file", d.name());
        assertNotNull(d.inputSchema());
    }

    @Test
    @DisplayName("replace single occurrence")
    void testReplaceSingle() throws Exception {
        var filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello world");
        var input = new ToolInput("c1", json(filePath, "world", "Java"));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("hello Java", Files.readString(filePath));
        assertTrue(result.content().contains("1"));
    }

    @Test
    @DisplayName("replace multiple occurrences")
    void testReplaceMultiple() throws Exception {
        var filePath = tempDir.resolve("multi.txt");
        Files.writeString(filePath, "foo foo foo bar");
        var input = new ToolInput("c2", json(filePath, "foo", "baz"));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("baz baz baz bar", Files.readString(filePath));
        assertTrue(result.content().contains("3"));
    }

    @Test
    @DisplayName("fail when file not found")
    void testFileNotFound() {
        var input = new ToolInput("c3", json(tempDir.resolve("nonexistent.txt"), "a", "b"));
        var result = tool.execute(input, null).block();
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("fail when oldText not in file")
    void testOldTextNotFound() throws Exception {
        var filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello world");
        var input = new ToolInput("c4", json(filePath, "xyz", "abc"));
        var result = tool.execute(input, null).block();
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("not found"));
    }

    @Test
    @DisplayName("replace with empty string effectively deletes text")
    void testReplaceWithEmpty() throws Exception {
        var filePath = tempDir.resolve("test.txt");
        Files.writeString(filePath, "hello world");
        var input = new ToolInput("c5", json(filePath, "world", ""));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("hello ", Files.readString(filePath));
    }

    @Test
    @DisplayName("replace multiline text")
    void testReplaceMultiline() throws Exception {
        var filePath = tempDir.resolve("multi.txt");
        Files.writeString(filePath, "line1\nline2\nline3");
        var input = new ToolInput("c6", json(filePath, "line2\nline3", "replaced"));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("line1\nreplaced", Files.readString(filePath));
    }
}
