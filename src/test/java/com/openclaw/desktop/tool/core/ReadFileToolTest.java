package com.openclaw.desktop.tool.core;

import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * ReadFileTool 测试。
 */
class ReadFileToolTest {

    @Test
    void testReadFile() throws Exception {
        // 创建临时文件
        var tempFile = Files.createTempFile("claw-test", ".txt");
        Files.writeString(tempFile, "Hello ClawDesktop!");

        var tool = new ReadFileTool();
        var input = new ToolInput("call_1",
            "{\"path\":\"" + tempFile.toAbsolutePath().toString().replace("\\", "/") + "\"}");
        var ctx = new ToolContext(new Session(SessionKey.main("default")), null, tempFile.getParent(), java.util.Map.of());

        var result = tool.execute(input, ctx).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.content().contains("Hello ClawDesktop"));

        Files.deleteIfExists(tempFile);
    }

    @Test
    void testReadNonExistentFile() {
        var tool = new ReadFileTool();
        var input = new ToolInput("call_1",
            "{\"path\":\"/nonexistent/file.txt\"}");
        var ctx = new ToolContext(new Session(SessionKey.main("default")), null, null, java.util.Map.of());

        var result = tool.execute(input, ctx).block();
        assertNotNull(result);
        assertFalse(result.success());
    }
}
