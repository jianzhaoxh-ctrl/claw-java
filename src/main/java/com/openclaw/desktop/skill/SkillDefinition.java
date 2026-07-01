package com.openclaw.desktop.skill;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 技能完整定义 — 由 {@link SkillMdParser} 解析 SKILL.md 得到。
 *
 * <p>SKILL.md 格式：
 * <pre>
 * ---
 * name: my-skill
 * description: 一句话描述
 * triggers:
 *   - "做某事"
 *   - "perform X"
 * allowed-tools:
 *   - read_file
 *   - write_file
 * enabled: true
 * ---
 * # 技能正文（Markdown 指令，注入到 Agent 系统提示）
 * ...</pre>
 *
 * @param name         技能名（唯一标识）
 * @param description  一句话描述
 * @param skillMdPath  SKILL.md 文件路径
 * @param content      frontmatter 之后的正文内容
 * @param triggers     触发词列表（用户输入匹配时激活技能）
 * @param allowedTools 允许使用的工具名（空表示不限制）
 * @param enabled      是否启用
 * @param metadata     其他 frontmatter 字段
 */
public record SkillDefinition(
    String name,
    String description,
    Path skillMdPath,
    String content,
    List<String> triggers,
    List<String> allowedTools,
    boolean enabled,
    Map<String, String> metadata
) {
    /**
     * 转为轻量描述符（不含正文，用于列表展示）。
     */
    public SkillDescriptor toDescriptor() {
        return new SkillDescriptor(name, description, skillMdPath.toString(), enabled);
    }
}
