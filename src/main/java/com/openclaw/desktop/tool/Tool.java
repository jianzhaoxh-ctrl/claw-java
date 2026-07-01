package com.openclaw.desktop.tool;

import com.openclaw.desktop.llm.ToolDescriptor;

/**
 * 工具接口 — 所有内置/插件工具实现此接口。
 * 对应 OpenClaw 的 Tool。
 */
public interface Tool {

    /** 工具描述符 */
    ToolDescriptor descriptor();

    /** 执行工具 */
    reactor.core.publisher.Mono<ToolResult> execute(ToolInput input, ToolContext context);
}
