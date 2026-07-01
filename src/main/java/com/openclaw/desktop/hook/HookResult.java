package com.openclaw.desktop.hook;

/**
 * 钩子结果 — 钩子执行后的返回值。
 */
public record HookResult(
    boolean shouldContinue,
    String modifiedContent,
    String injectContent,
    String blockReason,
    java.util.Map<String, Object> extraData
) {
    /**
     * 继续，不做修改。
     */
    public static HookResult pass() {
        return new HookResult(true, null, null, null, java.util.Map.of());
    }

    /**
     * 修改内容。
     */
    public static HookResult modify(String newContent) {
        return new HookResult(true, newContent, null, null, java.util.Map.of());
    }

    /**
     * 注入额外内容。
     */
    public static HookResult inject(String content) {
        return new HookResult(true, null, content, null, java.util.Map.of());
    }

    /**
     * 阻止操作。
     */
    public static HookResult block(String reason) {
        return new HookResult(false, null, null, reason, java.util.Map.of());
    }
}
