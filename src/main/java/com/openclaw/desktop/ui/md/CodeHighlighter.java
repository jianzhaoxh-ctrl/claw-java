package com.openclaw.desktop.ui.md;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语法高亮引擎 — 为 Java/Python/JavaScript/SQL/JSON/Shell 代码生成着色后的文本片段序列。
 *
 * <p>纯 Java 实现，不依赖 RichTextFX 或外部库。基于正则的简易词法分析：
 * <ul>
 *   <li>关键字（语言特定）</li>
 *   <li>字符串（单引号/双引号/三引号/模板字符串）</li>
 *   <li>注释（行注释 / 块注释）</li>
 *   <li>数字字面量</li>
 *   <li>注解/装饰器（@Annotation）</li>
 * </ul>
 *
 * <p>输出为 {@link Segment} 列表，每个片段携带文本与 {@link TokenType}，
 * 由 {@link CodeBlockRenderer} 映射为 JavaFX Text 节点的样式。
 */
public final class CodeHighlighter {

    private CodeHighlighter() {}

    /** 代码片段 — 一段文本及其 token 类型。 */
    public record Segment(String text, TokenType type) {}

    /** Token 类型 — 决定渲染颜色。 */
    public enum TokenType {
        PLAIN,      // 普通文本
        KEYWORD,    // 关键字
        STRING,     // 字符串
        COMMENT,    // 注释
        NUMBER,     // 数字
        ANNOTATION, // 注解（@Override）
        FUNCTION,   // 函数名
        OPERATOR,   // 运算符
        TYPE        // 类型名（大写开头）
    }

    /** 支持的语言。 */
    public enum Language {
        JAVA, PYTHON, JAVASCRIPT, TYPESCRIPT, SQL, JSON, SHELL, BASH, XML, HTML, CSS, MARKDOWN, PLAIN
    }

    /** 关键字表。 */
    private static final Map<Language, Set<String>> KEYWORDS = Map.ofEntries(
        Map.entry(Language.JAVA, Set.of(
            "abstract","assert","boolean","break","byte","case","catch","char","class","const",
            "continue","default","do","double","else","enum","extends","final","finally","float",
            "for","goto","if","implements","import","instanceof","int","interface","long","native",
            "new","package","private","protected","public","return","short","static","strictfp",
            "super","switch","synchronized","this","throw","throws","transient","try","void",
            "volatile","while","var","record","sealed","permits","yield","true","false","null")),
        Map.entry(Language.PYTHON, Set.of(
            "and","as","assert","async","await","break","class","continue","def","del","elif",
            "else","except","finally","for","from","global","if","import","in","is","lambda",
            "nonlocal","not","or","pass","raise","return","try","while","with","yield","True",
            "False","None","self","cls")),
        Map.entry(Language.JAVASCRIPT, Set.of(
            "abstract","async","await","break","case","catch","class","const","continue","debugger",
            "default","delete","do","else","enum","export","extends","false","finally","for",
            "function","if","implements","import","in","instanceof","interface","let","new","null",
            "package","private","protected","public","return","super","switch","static","this",
            "throw","try","true","typeof","undefined","var","void","while","with","yield")),
        Map.entry(Language.TYPESCRIPT, Set.of(
            "abstract","any","as","async","await","boolean","break","case","catch","class","const",
            "continue","debugger","declare","default","delete","do","else","enum","export","extends",
            "false","finally","for","from","function","get","if","implements","import","in",
            "instanceof","interface","let","namespace","never","new","null","number","of","private",
            "protected","public","readonly","return","set","static","string","super","switch",
            "symbol","this","throw","true","try","type","typeof","undefined","var","void","while",
            "with","yield")),
        Map.entry(Language.SQL, Set.of(
            "SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET","DELETE","CREATE",
            "TABLE","ALTER","DROP","INDEX","VIEW","JOIN","LEFT","RIGHT","INNER","OUTER","ON","AS",
            "AND","OR","NOT","NULL","IN","LIKE","BETWEEN","IS","ORDER","BY","GROUP","HAVING",
            "LIMIT","OFFSET","DISTINCT","UNION","ALL","CASE","WHEN","THEN","ELSE","END","COUNT",
            "SUM","AVG","MIN","MAX","PRIMARY","KEY","FOREIGN","REFERENCES","DEFAULT","UNIQUE",
            "CONSTRAINT","CHECK","CASCADE","INTEGER","VARCHAR","TEXT","BOOLEAN","DATE","TIME",
            "TIMESTAMP","DECIMAL","FLOAT","DOUBLE","CHAR","BLOB","select","from","where","insert",
            "into","values","update","set","delete","create","table","alter","drop","index","view",
            "join","left","right","inner","outer","on","as","and","or","not","null","in","like",
            "between","is","order","by","group","having","limit","offset","distinct","union","all",
            "case","when","then","else","end","count","sum","avg","min","max","primary","key",
            "foreign","references","default","unique","constraint","check","cascade")),
        Map.entry(Language.JSON, Set.of(
            "true","false","null")),
        Map.entry(Language.SHELL, Set.of(
            "if","then","else","elif","fi","for","do","done","while","case","esac","in","function",
            "return","local","export","unset","readonly","declare","echo","printf","read","exit",
            "cd","pwd","ls","cat","grep","sed","awk","find","mkdir","rm","cp","mv","touch","chmod",
            "chown","sudo","apt","yum","dnf","systemctl","service")),
        Map.entry(Language.BASH, Set.of(
            "if","then","else","elif","fi","for","do","done","while","case","esac","in","function",
            "return","local","export","unset","readonly","declare","echo","printf","read","exit"))
    );

    /** 语言别名解析。 */
    public static Language resolveLanguage(String name) {
        if (name == null || name.isBlank()) return Language.PLAIN;
        var lower = name.trim().toLowerCase();
        return switch (lower) {
            case "java" -> Language.JAVA;
            case "py", "python" -> Language.PYTHON;
            case "js", "javascript", "node" -> Language.JAVASCRIPT;
            case "ts", "typescript" -> Language.TYPESCRIPT;
            case "sql", "mysql", "postgres", "postgresql", "sqlite" -> Language.SQL;
            case "json" -> Language.JSON;
            case "sh", "shell", "zsh" -> Language.SHELL;
            case "bash" -> Language.BASH;
            case "xml", "html", "svg" -> Language.XML;
            case "css" -> Language.CSS;
            case "md", "markdown" -> Language.MARKDOWN;
            default -> Language.PLAIN;
        };
    }

    /**
     * 对代码进行语法高亮，返回片段序列。
     */
    public static List<Segment> highlight(String code, Language language) {
        if (language == Language.PLAIN || code == null || code.isEmpty()) {
            return List.of(new Segment(code, TokenType.PLAIN));
        }
        var result = new ArrayList<Segment>();
        var keywords = KEYWORDS.getOrDefault(language, Set.of());

        // 按行处理，先处理块注释/三引号字符串
        var i = 0;
        var len = code.length();
        var builder = new StringBuilder();

        while (i < len) {
            // 块注释 /* */ （Java/JS/TS/CSS）
            if (isBlockCommentStart(code, i, language)) {
                flushPlain(result, builder);
                var end = code.indexOf("*/", i + 2);
                if (end < 0) end = len; else end += 2;
                result.add(new Segment(code.substring(i, end), TokenType.COMMENT));
                i = end;
                continue;
            }
            // Python 三引号字符串
            if (language == Language.PYTHON && code.startsWith("\"\"\"", i)) {
                flushPlain(result, builder);
                var end = code.indexOf("\"\"\"", i + 3);
                if (end < 0) end = len; else end += 3;
                result.add(new Segment(code.substring(i, end), TokenType.STRING));
                i = end;
                continue;
            }
            // 行注释
            var lineCommentPrefix = lineCommentPrefix(language);
            if (lineCommentPrefix != null && code.startsWith(lineCommentPrefix, i)) {
                flushPlain(result, builder);
                var end = code.indexOf('\n', i);
                if (end < 0) end = len;
                result.add(new Segment(code.substring(i, end), TokenType.COMMENT));
                i = end;
                continue;
            }
            // 字符串（单/双引号）
            var c = code.charAt(i);
            if (c == '"' || c == '\'') {
                flushPlain(result, builder);
                var end = findStringEnd(code, i, c);
                result.add(new Segment(code.substring(i, end), TokenType.STRING));
                i = end;
                continue;
            }
            // 模板字符串（JS/TS）
            if ((language == Language.JAVASCRIPT || language == Language.TYPESCRIPT) && c == '`') {
                flushPlain(result, builder);
                var end = findStringEnd(code, i, '`');
                result.add(new Segment(code.substring(i, end), TokenType.STRING));
                i = end;
                continue;
            }
            // 注解（@Override）
            if (c == '@' && (language == Language.JAVA || language == Language.TYPESCRIPT)) {
                flushPlain(result, builder);
                var m = Pattern.compile("@[A-Za-z_][A-Za-z0-9_]*").matcher(code);
                m.region(i, len);
                if (m.lookingAt()) {
                    result.add(new Segment(m.group(), TokenType.ANNOTATION));
                    i = m.end();
                    continue;
                }
            }
            // 数字
            if (Character.isDigit(c)) {
                flushPlain(result, builder);
                var m = Pattern.compile("\\d[\\d_]*\\.?[\\d_]*([eE][+-]?\\d+)?[fFdDlL]?").matcher(code);
                m.region(i, len);
                if (m.lookingAt()) {
                    result.add(new Segment(m.group(), TokenType.NUMBER));
                    i = m.end();
                    continue;
                }
            }
            // 标识符（关键字 / 类型 / 函数）
            if (Character.isLetter(c) || c == '_') {
                flushPlain(result, builder);
                var m = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(code);
                m.region(i, len);
                if (m.lookingAt()) {
                    var word = m.group();
                    var end = m.end();
                    if (keywords.contains(word)) {
                        result.add(new Segment(word, TokenType.KEYWORD));
                    } else if (end < len && code.charAt(end) == '(') {
                        result.add(new Segment(word, TokenType.FUNCTION));
                    } else if (Character.isUpperCase(word.charAt(0))) {
                        result.add(new Segment(word, TokenType.TYPE));
                    } else {
                        result.add(new Segment(word, TokenType.PLAIN));
                    }
                    i = end;
                    continue;
                }
            }
            // 默认：累积为普通文本
            builder.append(c);
            i++;
        }
        flushPlain(result, builder);
        return result;
    }

    private static void flushPlain(List<Segment> result, StringBuilder builder) {
        if (builder.length() > 0) {
            result.add(new Segment(builder.toString(), TokenType.PLAIN));
            builder.setLength(0);
        }
    }

    private static boolean isBlockCommentStart(String code, int i, Language lang) {
        if (lang == Language.JAVA || lang == Language.JAVASCRIPT || lang == Language.TYPESCRIPT
            || lang == Language.CSS) {
            return code.startsWith("/*", i);
        }
        return false;
    }

    private static String lineCommentPrefix(Language lang) {
        return switch (lang) {
            case JAVA, JAVASCRIPT, TYPESCRIPT, CSS, SQL -> "//";
            case PYTHON, SHELL, BASH -> "#";
            case JSON, XML, HTML, MARKDOWN, PLAIN -> null;
        };
    }

    private static int findStringEnd(String code, int start, char quote) {
        var i = start + 1;
        while (i < code.length()) {
            var c = code.charAt(i);
            if (c == '\\' && i + 1 < code.length()) {
                i += 2; // 跳过转义
                continue;
            }
            if (c == quote) return i + 1;
            if (c == '\n') return i; // 换行即结束（容错）
            i++;
        }
        return code.length();
    }
}
