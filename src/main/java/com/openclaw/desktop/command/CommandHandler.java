package com.openclaw.desktop.command;

import reactor.core.publisher.Mono;

/**
 * 命令处理器接口 — 所有斜杠命令实现此接口。
 */
public interface CommandHandler {

    /**
     * 命令定义。
     */
    CommandDefinition definition();

    /**
     * 执行命令。
     */
    Mono<CommandResult> execute(CommandInput input, CommandContext context);
}
