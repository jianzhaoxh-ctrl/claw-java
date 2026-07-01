package com.openclaw.desktop.agent;

/**
 * 推理级别 — 对应 OpenClaw 的 ReasoningLevel。
 */
public enum ReasoningLevel {
    OFF, LOW, MEDIUM, HIGH;

    public static ReasoningLevel fromString(String s) {
        if (s == null) return OFF;
        return switch (s.toLowerCase()) {
            case "low" -> LOW;
            case "medium" -> MEDIUM;
            case "high" -> HIGH;
            default -> OFF;
        };
    }
}
