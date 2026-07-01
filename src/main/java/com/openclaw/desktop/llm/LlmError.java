package com.openclaw.desktop.llm;

/**
 * LLM 错误类型 — sealed interface 表达所有可能的 LLM 错误。
 * 对应 OpenClaw 的 LLM error 分类。
 */
public sealed interface LlmError {

    /** API Key 无效或过期 */
    record AuthError(String providerId, String message) implements LlmError {}

    /** 请求限速 (429) */
    record RateLimitError(String providerId, int retryAfterSeconds, String message) implements LlmError {}

    /** 请求参数错误 (400) */
    record InvalidRequestError(String providerId, String message) implements LlmError {}

    /** 模型不存在或不可用 (404) */
    record ModelNotFoundError(String providerId, String modelId, String message) implements LlmError {}

    /** 服务端错误 (500/502/503) */
    record ServerError(String providerId, int statusCode, String message) implements LlmError {}

    /** 上下文长度超限 */
    record ContextLengthError(String providerId, int tokenCount, int maxTokens, String message) implements LlmError {}

    /** 内容过滤/安全拒绝 */
    record ContentFilterError(String providerId, String reason, String message) implements LlmError {}

    /** 连接超时 */
    record TimeoutError(String providerId, String message) implements LlmError {}

    /** 余额不足 */
    record QuotaExceededError(String providerId, String message) implements LlmError {}

    /** 网络连接失败 */
    record ConnectionError(String providerId, String message) implements LlmError {}

    /** 未知错误 */
    record UnknownError(String providerId, String message, Throwable cause) implements LlmError {}

    /**
     * 从 HTTP 状态码和响应体解析错误类型。
     */
    static LlmError fromStatusCode(String providerId, int statusCode, String body) {
        return switch (statusCode) {
            case 400 -> new InvalidRequestError(providerId, body);
            case 401, 403 -> new AuthError(providerId, body);
            case 404 -> new ModelNotFoundError(providerId, "", body);
            case 429 -> {
                // 尝试解析 retry-after
                var retryAfter = 60; // 默认60秒
                if (body != null && body.contains("retry-after")) {
                    try { retryAfter = Integer.parseInt(body.replaceAll(".*retry-after[^0-9]*([0-9]+).*", "$1")); }
                    catch (Exception ignored) {}
                }
                yield new RateLimitError(providerId, retryAfter, body);
            }
            case 500, 502, 503 -> new ServerError(providerId, statusCode, body);
            default -> new UnknownError(providerId, "HTTP " + statusCode + ": " + body, null);
        };
    }

    /**
     * 是否可重试。
     */
    default boolean isRetryable() {
        return switch (this) {
            case RateLimitError(var p, var r, var m) -> true;
            case ServerError(var p, var s, var m) -> true;
            case TimeoutError(var p, var m) -> true;
            case ConnectionError(var p, var m) -> true;
            case AuthError(var p, var m) -> false;
            case InvalidRequestError(var p, var m) -> false;
            case ModelNotFoundError(var p, var id, var m) -> false;
            case ContextLengthError(var p, var tc, var mt, var m) -> false;
            case ContentFilterError(var p, var r, var m) -> false;
            case QuotaExceededError(var p, var m) -> false;
            case UnknownError(var p, var m, var c) -> false;
        };
    }

    /**
     * 建议的重试等待时间（毫秒）。
     */
    default long suggestedRetryDelayMs() {
        return switch (this) {
            case RateLimitError(var p, var retryAfter, var m) -> retryAfter * 1000L;
            case ServerError(var p, var s, var m) -> 2000L;
            case TimeoutError(var p, var m) -> 3000L;
            case ConnectionError(var p, var m) -> 1000L;
            default -> 0L;
        };
    }
}
