package com.openclaw.desktop.skill;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 技能管理器 — 技能系统的上层门面，提供按需匹配与系统提示注入能力。
 *
 * <p>职责：
 * <ul>
 *   <li>从文件系统发现技能（{@link #loadAll}）</li>
 *   <li>从 classpath 加载内置技能（{@link #loadBuiltin}）</li>
 *   <li>按用户输入匹配触发词（{@link #match}）</li>
 *   <li>构建注入到 Agent 系统提示的技能指令（{@link #buildSystemPromptInjection}）</li>
 *   <li>运行时启用/禁用技能</li>
 * </ul>
 */
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);

    /** 内置技能名（对应 classpath: skills/<name>/SKILL.md）。 */
    private static final List<String> BUILTIN_SKILLS = List.of(
        "file-operations", "web-search", "code-review"
    );

    private final SkillRegistry registry;
    /** 运行时禁用的技能名集合（不改 SkillDefinition 本身）。 */
    private final Set<String> disabled = ConcurrentHashMap.newKeySet();

    public SkillManager(SkillRegistry registry) {
        this.registry = registry;
    }

    /** 从 skills 目录发现技能。 */
    public void loadAll() {
        registry.discover();
        log.info("SkillManager loaded {} skill(s) from disk", registry.size());
    }

    /** 从 classpath 加载内置技能。 */
    public void loadBuiltin() {
        var cl = getClass().getClassLoader();
        int loaded = 0;
        for (var name : BUILTIN_SKILLS) {
            var resourcePath = "skills/" + name + "/SKILL.md";
            var url = cl.getResource(resourcePath);
            if (url == null) {
                log.debug("Builtin skill not found on classpath: {}", resourcePath);
                continue;
            }
            try (var in = url.openStream()) {
                var content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                var def = SkillMdParser.parse(content, Path.of(resourcePath));
                registry.register(def);
                loaded++;
            } catch (Exception e) {
                log.warn("Failed to load builtin skill {}: {}", name, e.getMessage());
            }
        }
        log.info("Loaded {} builtin skill(s)", loaded);
    }

    /**
     * 按用户输入匹配启用的技能（基于 triggers 触发词，大小写不敏感，包含匹配）。
     * @return 匹配的技能列表（已启用且未被运行时禁用）
     */
    public List<SkillDefinition> match(String userInput) {
        if (userInput == null || userInput.isBlank()) return List.of();
        var lower = userInput.toLowerCase();
        return registry.allDefinitions().stream()
            .filter(d -> !disabled.contains(d.name()))
            .filter(d -> d.triggers() != null && !d.triggers().isEmpty())
            .filter(d -> d.triggers().stream()
                .anyMatch(t -> t != null && !t.isBlank() && lower.contains(t.toLowerCase())))
            .collect(Collectors.toList());
    }

    /**
     * 构建注入到 Agent 系统提示的技能指令（包含所有启用技能）。
     */
    public String buildSystemPromptInjection() {
        var active = registry.allDefinitions().stream()
            .filter(d -> !disabled.contains(d.name()))
            .toList();
        return buildInjection(active);
    }

    /**
     * 构建指定技能的注入指令。
     */
    public String buildSystemPromptInjection(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) return "";
        var defs = skillNames.stream()
            .flatMap(n -> registry.getDefinition(n).stream())
            .filter(d -> !disabled.contains(d.name()))
            .toList();
        return buildInjection(defs);
    }

    private String buildInjection(List<SkillDefinition> defs) {
        if (defs.isEmpty()) return "";
        var sb = new StringBuilder();
        sb.append("\n\n## 可用技能\n请根据用户意图，按需遵循以下技能指令：\n\n");
        for (var d : defs) {
            sb.append("### 技能: ").append(d.name()).append("\n");
            if (!d.description().isBlank()) {
                sb.append("> ").append(d.description()).append("\n\n");
            }
            sb.append(d.content()).append("\n\n");
        }
        return sb.toString().trim();
    }

    // ---- 运行时控制 ----

    public void enable(String name) {
        disabled.remove(name);
        log.debug("Skill enabled: {}", name);
    }

    public void disable(String name) {
        disabled.add(name);
        log.debug("Skill disabled: {}", name);
    }

    public boolean isEnabled(String name) {
        return !disabled.contains(name);
    }

    // ---- 查询 ----

    public Optional<SkillDefinition> get(String name) {
        return registry.getDefinition(name);
    }

    /** 所有启用的技能（运行时未禁用）。 */
    public Collection<SkillDefinition> all() {
        return registry.allDefinitions().stream()
            .filter(d -> !disabled.contains(d.name()))
            .toList();
    }

    /** 注册一个技能定义（插件可调用）。 */
    public void register(SkillDefinition definition) {
        registry.register(definition);
    }

    public int count() {
        return (int) registry.allDefinitions().stream()
            .filter(d -> !disabled.contains(d.name()))
            .count();
    }
}
