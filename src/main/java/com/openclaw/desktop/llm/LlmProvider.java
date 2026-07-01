package com.openclaw.desktop.llm;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * LLM 提供商接口 — 所有 LLM Provider 实现此接口。
 * 对应 OpenClaw 的 LlmProvider。
 */
public interface LlmProvider {

    /** Provider 唯一标识 */
    String id();

    /** Provider 显示名称 */
    String name();

    /** 同步聊天 */
    Mono<LlmResponse> chat(LlmRequest request);

    /** 流式聊天 */
    Flux<LlmEvent> chatStream(LlmRequest request);

    /** 模型列表 */
    Flux<Model> listModels();

    /** 健康检查 */
    Mono<Boolean> healthCheck();
}
