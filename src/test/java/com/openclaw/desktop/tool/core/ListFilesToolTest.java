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
 * ListFilesTool 单元测试。
 */
class ListFilesToolTest {

    @TempDir
    Path tempDir;

    private final ListFilesTool tool = new ListFilesTool();
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
        assertEquals("list_files", d.name());
    }

    @Test
    @DisplayName("list files in directory")
    void testListFiles() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "aaa");
        Files.writeString(tempDir.resolve("b.txt"), "bbb");
        var input = new ToolInput("c1", json(tempDir));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertTrue(result.content().contains("a.txt"));
        assertTrue(result.content().contains("b.txt"));
    }

    @Test
    @DisplayName("list includes file type indicator")
    void testListTypeIndicator() throws Exception {
        Files.writeString(tempDir.resolve("file.txt"), "x");
        Files.createDirectory(tempDir.resolve("subdir"));
        var input = new ToolInput("c2", json(tempDir));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertTrue(result.content().contains("FILE"));
        assertTrue(result.content().contains("DIR"));
    }

    @Test
    @DisplayName("list empty directory returns empty result")
    void testListEmptyDir() {
        var input = new ToolInput("c3", json(tempDir));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertNotNull(result.content());
    }

    @Test
    @DisplayName("list non-directory fails")
    void testListNonDirectory() throws Exception {
        var filePath = tempDir.resolve("notdir.txt");
        Files.writeString(filePath, "x");
        var input = new ToolInput("c4", json(filePath));
        var result = tool.execute(input, null).block();
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Not a directory"));
    }

    @Test
    @DisplayName("list shows file sizes")
    void testListShowsSize() throws Exception {
        Files.writeString(tempDir.resolve("sized.txt"), "12345");
        var input = new ToolInput("c5", json(tempDir));
        var result = tool.execute(input, null).block();
        assertTrue(result.success());
        assertTrue(result.content().contains("5"));
    }
}
