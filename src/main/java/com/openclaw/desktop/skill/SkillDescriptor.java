package com.openclaw.desktop.skill;

/**
 * Skill 定义 — 对应 OpenClaw 的 Skill。
 * Skill 是一组可复用的指令/流程，Agent 可以按需加载。
 */
public record SkillDescriptor(
    String name,
    String description,
    String skillPath,
    boolean enabled
) {
    public static SkillDescriptor of(String name, String description, String path) {
        return new SkillDescriptor(name, description, path, true);
    }
}
