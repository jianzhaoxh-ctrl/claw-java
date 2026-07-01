package com.openclaw.desktop.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * LLM 重试策略 — 统一的错误重试逻辑。
 * 对应 OpenClaw 的 LLM retry / fallback 机制。
 *
 * <p>核心策略：
 * <ul>
 *   <li>可重试错误（429限速、500服务端错误、超时）→ 自动重试</li>
 *   <li>不可重试错误（401认证、400参数、上下文超限）→ 立即失败</li>
 *   <li>限速 → 使用 retry-after 延迟</li>
 *   <li>服务端错误 → 递增延迟（1s → 2s → 4s）</li>
 *   <li>最大重试次数可配置</li>
 * </ul>
 */
public class LlmRetryStrategy {
    private static final Logger log = LoggerFactory.getLogger(LlmRetryStrategy.class);

    private final int maxRetries;
    private final Duration initialDelay;
    private final Duration maxDelay;
    private final double multiplier;

    public LlmRetryStrategy() {
        this(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);
    }

    public LlmRetryStrategy(int maxRetries, Duration initialDelay, Duration maxDelay, double multiplier) {
        this.maxRetries = maxRetries;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
        this.multiplier = multiplier;
    }

    /**
     * 为 Mono（同步调用）创建重试包装。
     */
    public <T> Mono<T> wrapWithRetry(Mono<T> source, String providerId) {
        return source.retryWhen(Retry.backoff(maxRetries, initialDelay)
            .maxBackoff(maxDelay)
            .jitter(0.3)
            .filter(this::isRetryableException)
            .doBeforeRetry(signal -> {
                var attempt = signal.totalRetries() + 1;
                log.warn("Retrying LLM call for {} (attempt {}): {}",
                    providerId, attempt, signal.failure().getMessage());
            })
            .onRetryExhaustedThrow((spec, signal) -> {
                log.error("Retry exhausted for {} after {} attempts", providerId, maxRetries);
                return signal.failure();
            })
        );
    }

    /**
     * 为 Flux（流式调用）创建重试包装。
     */
    public <T> Flux<T> wrapWithRetry(Flux<T> source, String providerId) {
        return source.retryWhen(Retry.backoff(maxRetries, initialDelay)
            .maxBackoff(maxDelay)
            .jitter(0.3)
            .filter(this::isRetryableException)
            .doBeforeRetry(signal -> {
                var attempt = signal.totalRetries() + 1;
                log.warn("Retrying LLM stream for {} (attempt {}): {}",
                    providerId, attempt, signal.failure().getMessage());
            })
        );
    }

    /**
     * 判断异常是否可重试。
     */
    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof RuntimeException rt) {
            var message = rt.getMessage();
            if (message == null) return false;
            // 限速
            if (message.contains("429") || message.contains("rate limit") || message.contains("Rate limit")) return true;
            // 服务端错误
            if (message.contains("500") || message.contains("502") || message.contains("503")) return true;
            // 超时
            if (message.contains("timeout") || message.contains("Timeout")) return true;
            // 连接失败
            if (message.contains("connection") || message.contains("Connection")) return true;
        }
        return false;
    }

    /**
     * 默认策略。
     */
    public static LlmRetryStrategy defaults() {
        return new LlmRetryStrategy();
    }

    /**
     * 无重试策略。
     */
    public static LlmRetryStrategy noRetry() {
        return new LlmRetryStrategy(0, Duration.ZERO, Duration.ZERO, 1.0);
    }

    /**
     * 激进重试策略（更多次数、更长延迟）。
     */
    public static LlmRetryStrategy aggressive() {
        return new LlmRetryStrategy(5, Duration.ofSeconds(2), Duration.ofSeconds(60), 2.0);
    }
}
