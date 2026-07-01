package com.openclaw.desktop.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * SKILL.md 解析器 — 解析 YAML frontmatter + Markdown 正文。
 *
 * <p>手动解析简单的 YAML frontmatter（不引入 snakeyaml 依赖），
 * 支持 {@code key: value} 标量和 {@code key:} 后接 {@code - item} 列表。
 *
 * <p>frontmatter 以 {@code ---} 开头和结尾。若无 frontmatter，则整体视为正文，
 * 技能名取文件父目录名，描述取正文首段。
 */
public final class SkillMdParser {

    private static final Logger log = LoggerFactory.getLogger(SkillMdParser.class);
    private static final String FRONTMATTER_DELIM = "---";

    private SkillMdParser() {}

    /**
     * 从文件解析技能定义。
     */
    public static SkillDefinition parse(Path skillMdPath) throws IOException {
        var content = Files.readString(skillMdPath);
        return parse(content, skillMdPath);
    }

    /**
     * 解析技能定义。
     * @param rawContent SKILL.md 原始内容
     * @param skillMdPath 文件路径（用于记录来源）
     */
    public static SkillDefinition parse(String rawContent, Path skillMdPath) {
        var name = skillMdPath != null && skillMdPath.getParent() != null
            ? skillMdPath.getParent().getFileName().toString()
            : "unknown";

        // 拆分 frontmatter 与正文
        var parts = splitFrontmatter(rawContent);
        Map<String, Object> frontmatter = parts.frontmatter;
        String body = parts.body;

        // 提取字段
        var skillName = asString(frontmatter.get("name"), name);
        var description = asString(frontmatter.get("description"),
            extractFirstParagraph(body));
        var triggers = asStringList(frontmatter.get("triggers"));
        var allowedTools = asStringList(frontmatter.get("allowed-tools"));
        var enabled = asBool(frontmatter.get("enabled"), true);

        // 其余字段归入 metadata
        var metadata = new LinkedHashMap<String, String>();
        for (var entry : frontmatter.entrySet()) {
            if (!Set.of("name", "description", "triggers", "allowed-tools", "enabled")
                .contains(entry.getKey())) {
                metadata.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return new SkillDefinition(
            skillName, description, skillMdPath, body.trim(),
            triggers, allowedTools, enabled, Map.copyOf(metadata)
        );
    }

    /** 拆分 frontmatter 与正文。 */
    private static FrontmatterSplit splitFrontmatter(String raw) {
        var lines = raw.split("\n", -1);
        // 寻找起始 ---
        int start = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_DELIM)) {
                start = i;
                break;
            }
            if (!lines[i].trim().isEmpty()) break; // 首行非空非 --- 则无 frontmatter
        }
        if (start < 0) {
            return new FrontmatterSplit(Map.of(), raw);
        }
        // 寻找结束 ---
        int end = -1;
        for (int i = start + 1; i < lines.length; i++) {
            if (lines[i].trim().equals(FRONTMATTER_DELIM)) {
                end = i;
                break;
            }
        }
        if (end < 0) {
            return new FrontmatterSplit(Map.of(), raw);
        }
        // 解析 frontmatter 行
        var fm = parseYamlLines(Arrays.copyOfRange(lines, start + 1, end));
        var body = String.join("\n", Arrays.copyOfRange(lines, end + 1, lines.length));
        return new FrontmatterSplit(fm, body);
    }

    /**
     * 解析简单 YAML 行（key: value 与 key: + 列表）。
     */
    private static Map<String, Object> parseYamlLines(String[] lines) {
        var result = new LinkedHashMap<String, Object>();
        String currentKey = null;
        List<String> currentList = null;

        for (var line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            // 列表项
            if (line.trim().startsWith("-") && currentKey != null) {
                if (currentList == null) {
                    currentList = new ArrayList<>();
                }
                var item = line.trim().substring(1).trim();
                currentList.add(stripQuotes(item));
                continue;
            }
            // key: value
            var colonIdx = indexOfColon(line);
            if (colonIdx > 0) {
                // 保存上一个列表
                if (currentKey != null && currentList != null) {
                    result.put(currentKey, currentList);
                    currentList = null;
                }
                var key = line.substring(0, colonIdx).trim();
                var value = line.substring(colonIdx + 1).trim();
                currentKey = key;
                if (value.isEmpty()) {
                    // 可能是列表起始，等待后续 - 行
                    currentList = null;
                } else {
                    result.put(key, stripQuotes(value));
                }
            }
        }
        // 保存最后一个列表
        if (currentKey != null && currentList != null) {
            result.put(currentKey, currentList);
        }
        return result;
    }

    private static int indexOfColon(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ':') {
                // 确保不是 URL 中的冒号（后面有空格或行尾）
                if (i + 1 >= line.length() || Character.isWhitespace(line.charAt(i + 1))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        if ((s.startsWith("\"") && s.endsWith("\""))
            || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String extractFirstParagraph(String body) {
        for (var line : body.split("\n")) {
            var t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            return t.length() > 160 ? t.substring(0, 160) : t;
        }
        return "";
    }

    // ---- 类型转换辅助 ----

    private static String asString(Object v, String def) {
        return v != null ? String.valueOf(v) : def;
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object v) {
        if (v == null) return List.of();
        if (v instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(v));
    }

    private static boolean asBool(Object v, boolean def) {
        if (v == null) return def;
        if (v instanceof Boolean b) return b;
        return switch (String.valueOf(v).toLowerCase()) {
            case "true", "yes", "1", "on" -> true;
            case "false", "no", "0", "off" -> false;
            default -> def;
        };
    }

    private record FrontmatterSplit(Map<String, Object> frontmatter, String body) {}
}
