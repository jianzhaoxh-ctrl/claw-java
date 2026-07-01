package com.openclaw.desktop.llm;

import com.openclaw.desktop.agent.ReasoningLevel;

import java.util.List;
import java.util.Map;

/**
 * LLM 请求。
 */
public record LlmRequest(
    String modelId,
    List<Message> messages,
    Double temperature,
    Integer maxTokens,
    ReasoningLevel reasoningLevel,
    List<ToolDescriptor> tools,
    String reasoningPrompt,
    Map<String, Object> extraParams
) {}
