package com.openclaw.desktop.ui.md;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 语法高亮引擎测试。
 */
class CodeHighlighterTest {

    @Test
    @DisplayName("resolveLanguage maps common aliases")
    void testResolveLanguage() {
        assertEquals(CodeHighlighter.Language.JAVA, CodeHighlighter.resolveLanguage("java"));
        assertEquals(CodeHighlighter.Language.PYTHON, CodeHighlighter.resolveLanguage("py"));
        assertEquals(CodeHighlighter.Language.PYTHON, CodeHighlighter.resolveLanguage("python"));
        assertEquals(CodeHighlighter.Language.JAVASCRIPT, CodeHighlighter.resolveLanguage("js"));
        assertEquals(CodeHighlighter.Language.JAVASCRIPT, CodeHighlighter.resolveLanguage("javascript"));
        assertEquals(CodeHighlighter.Language.TYPESCRIPT, CodeHighlighter.resolveLanguage("ts"));
        assertEquals(CodeHighlighter.Language.SQL, CodeHighlighter.resolveLanguage("mysql"));
        assertEquals(CodeHighlighter.Language.SHELL, CodeHighlighter.resolveLanguage("sh"));
        assertEquals(CodeHighlighter.Language.PLAIN, CodeHighlighter.resolveLanguage("unknown"));
        assertEquals(CodeHighlighter.Language.PLAIN, CodeHighlighter.resolveLanguage(""));
        assertEquals(CodeHighlighter.Language.PLAIN, CodeHighlighter.resolveLanguage(null));
    }

    @Test
    @DisplayName("Java keywords are highlighted")
    void testJavaKeywords() {
        var segments = CodeHighlighter.highlight("public class Foo {}", CodeHighlighter.Language.JAVA);
        assertFalse(segments.isEmpty());
        // 应包含 KEYWORD 类型的片段（public, class）
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.KEYWORD));
    }

    @Test
    @DisplayName("strings are highlighted")
    void testStringHighlight() {
        var segments = CodeHighlighter.highlight("var s = \"hello\";", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.STRING
            && s.text().contains("hello")));
    }

    @Test
    @DisplayName("line comments are highlighted")
    void testLineComment() {
        var segments = CodeHighlighter.highlight("// a comment\ncode", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.COMMENT
            && s.text().contains("comment")));
    }

    @Test
    @DisplayName("block comments are highlighted")
    void testBlockComment() {
        var segments = CodeHighlighter.highlight("/* block */ code", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.COMMENT
            && s.text().contains("block")));
    }

    @Test
    @DisplayName("Python triple-quoted strings")
    void testPythonTripleQuote() {
        var segments = CodeHighlighter.highlight("\"\"\"docstring\"\"\"\nx = 1", CodeHighlighter.Language.PYTHON);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.STRING
            && s.text().contains("docstring")));
    }

    @Test
    @DisplayName("numbers are highlighted")
    void testNumberHighlight() {
        var segments = CodeHighlighter.highlight("x = 42", CodeHighlighter.Language.PYTHON);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.NUMBER
            && s.text().contains("42")));
    }

    @Test
    @DisplayName("annotations are highlighted in Java")
    void testAnnotation() {
        var segments = CodeHighlighter.highlight("@Override\npublic void run() {}", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.ANNOTATION
            && s.text().equals("@Override")));
    }

    @Test
    @DisplayName("function calls are highlighted")
    void testFunctionHighlight() {
        var segments = CodeHighlighter.highlight("println(\"hi\")", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.FUNCTION
            && s.text().equals("println")));
    }

    @Test
    @DisplayName("type names (uppercase) are highlighted")
    void testTypeHighlight() {
        var segments = CodeHighlighter.highlight("Foo bar = new Foo();", CodeHighlighter.Language.JAVA);
        assertTrue(segments.stream().anyMatch(s -> s.type() == CodeHighlighter.TokenType.TYPE
            && s.text().equals("Foo")));
    }

    @Test
    @DisplayName("PLAIN language returns single plain segment")
    void testPlainLanguage() {
        var segments = CodeHighlighter.highlight("anything goes here", CodeHighlighter.Language.PLAIN);
        assertEquals(1, segments.size());
        assertEquals(CodeHighlighter.TokenType.PLAIN, segments.get(0).type());
    }

    @Test
    @DisplayName("null or empty code returns single plain segment")
    void testNullOrEmpty() {
        assertEquals(1, CodeHighlighter.highlight(null, CodeHighlighter.Language.JAVA).size());
        assertEquals(1, CodeHighlighter.highlight("", CodeHighlighter.Language.JAVA).size());
    }

    @Test
    @DisplayName("SQL keywords case-insensitive")
    void testSqlKeywords() {
        var segments = CodeHighlighter.highlight("SELECT * FROM users", CodeHighlighter.Language.SQL);
        var keywords = segments.stream()
            .filter(s -> s.type() == CodeHighlighter.TokenType.KEYWORD)
            .map(CodeHighlighter.Segment::text)
            .toList();
        assertTrue(keywords.contains("SELECT"));
        assertTrue(keywords.contains("FROM"));
    }
}
