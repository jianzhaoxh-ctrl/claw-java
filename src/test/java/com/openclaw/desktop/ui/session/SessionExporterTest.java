package com.openclaw.desktop.ui.session;

import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.TranscriptEntry;
import com.openclaw.desktop.ui.session.SessionExporter;
import com.openclaw.desktop.ui.session.SessionExporter.Format;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SessionExporterTest {

    @TempDir
    Path tempDir;

    private Session session;
    private SessionExporter exporter;

    @BeforeEach
    void setUp() {
        session = new Session(SessionKey.main("export-test"));
        session.addUserMessage("Hello, how are you?");
        session.addAssistantMessage("I'm doing great! How can I help you today?");
        exporter = new SessionExporter();
    }

    @Test
    void testExportMarkdown() throws Exception {
        var path = exporter.export(session, Format.MARKDOWN, tempDir);
        assertTrue(Files.exists(path));
        var content = Files.readString(path);
        assertTrue(content.contains("# Session Export"));
        assertTrue(content.contains("👤 User"));
        assertTrue(content.contains("🤖 Assistant"));
        assertTrue(content.contains("Hello, how are you?"));
        assertTrue(content.contains("I'm doing great!"));
    }

    @Test
    void testExportJson() throws Exception {
        var path = exporter.export(session, Format.JSON, tempDir);
        assertTrue(Files.exists(path));
        var content = Files.readString(path);
        assertTrue(content.contains("sessionKey"));
        assertTrue(content.contains("messages"));
        assertTrue(content.contains("Hello, how are you?"));
    }

    @Test
    void testExportHtml() throws Exception {
        var path = exporter.export(session, Format.HTML, tempDir);
        assertTrue(Files.exists(path));
        var content = Files.readString(path);
        assertTrue(content.contains("<!DOCTYPE html>"));
        assertTrue(content.contains("user-msg"));
        assertTrue(content.contains("assistant-msg"));
        assertTrue(content.contains("Hello, how are you?"));
    }

    @Test
    void testExportEmptySession() throws Exception {
        var emptySession = new Session(SessionKey.main("empty"));
        var path = exporter.export(emptySession, Format.MARKDOWN, tempDir);
        assertTrue(Files.exists(path));
        var content = Files.readString(path);
        assertTrue(content.contains("# Session Export"));
    }

    @Test
    void testExportCreatesDir() throws Exception {
        var nestedDir = tempDir.resolve("nested").resolve("sub");
        var path = exporter.export(session, Format.JSON, nestedDir);
        assertTrue(Files.exists(path));
    }

    @Test
    void testFormatEnum() {
        assertEquals(3, Format.values().length);
    }
}
