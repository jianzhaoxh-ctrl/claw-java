package com.openclaw.desktop.cron;

/**
 * Cron 任务负载。
 */
public record CronPayload(
    Kind kind,
    String text,
    String message,
    String model,
    String thinking,
    int timeoutSeconds
) {
    public enum Kind { SYSTEM_EVENT, AGENT_TURN }

    public static CronPayload systemEvent(String text) {
        return new CronPayload(Kind.SYSTEM_EVENT, text, null, null, null, 0);
    }

    public static CronPayload agentTurn(String message, String model) {
        return new CronPayload(Kind.AGENT_TURN, null, message, model, null, 0);
    }

    public static CronPayload agentTurn(String message, String model, int timeoutSeconds) {
        return new CronPayload(Kind.AGENT_TURN, null, message, model, null, timeoutSeconds);
    }
}
