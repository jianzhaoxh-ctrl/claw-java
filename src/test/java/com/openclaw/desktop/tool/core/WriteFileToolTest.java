package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.tool.ToolInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WriteFileTool 单元测试。
 */
class WriteFileToolTest {

    @TempDir
    Path tempDir;

    private final WriteFileTool tool = new WriteFileTool();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 构造 JSON 参数，自动处理路径转义。 */
    private String json(Path path, String content) {
        var node = MAPPER.createObjectNode();
        node.put("path", path.toString());
        node.put("content", content);
        return node.toString();
    }

    @Test
    @DisplayName("descriptor has correct name and required fields")
    void testDescriptor() {
        var d = tool.descriptor();
        assertEquals("write_file", d.name());
        assertNotNull(d.inputSchema());
    }

    @Test
    @DisplayName("write creates new file with content")
    void testWriteNewFile() throws Exception {
        var filePath = tempDir.resolve("test.txt");
        var input = new ToolInput("call-1", json(filePath, "hello world"));
        var result = tool.execute(input, null).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.content().contains("test.txt"));
        assertEquals("hello world", Files.readString(filePath));
    }

    @Test
    @DisplayName("write creates parent directories if missing")
    void testWriteCreatesParentDirs() throws Exception {
        var filePath = tempDir.resolve("a/b/c/test.txt");
        var input = new ToolInput("call-2", json(filePath, "nested"));
        var result = tool.execute(input, null).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertEquals("nested", Files.readString(filePath));
    }

    @Test
    @DisplayName("write overwrites existing file")
    void testWriteOverwrite() throws Exception {
        var filePath = tempDir.resolve("existing.txt");
        Files.writeString(filePath, "old content");
        var input = new ToolInput("call-3", json(filePath, "new content"));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("new content", Files.readString(filePath));
    }

    @Test
    @DisplayName("write empty content succeeds")
    void testWriteEmpty() throws Exception {
        var filePath = tempDir.resolve("empty.txt");
        var input = new ToolInput("call-4", json(filePath, ""));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("", Files.readString(filePath));
    }

    @Test
    @DisplayName("write unicode content")
    void testWriteUnicode() throws Exception {
        var filePath = tempDir.resolve("unicode.txt");
        var input = new ToolInput("call-5", json(filePath, "你好世界 🌍"));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertEquals("你好世界 🌍", Files.readString(filePath));
    }
}
