package com.openclaw.desktop.command;

import com.openclaw.desktop.command.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 命令管理器 — 籇命令的入口，注册内置命令， 分发执行。
 * 对应 OpenClaw 的 CLI command 入口。
 */
public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final CommandRegistry registry;

    public CommandManager() {
        this.registry = new CommandRegistry();
        registerBuiltinCommands();
    }

    /**
     * 注册所有内置命令。
     */
    private void registerBuiltinCommands() {
        registry.register(new StatusCommand());
        registry.register(new ModelsCommand());
        registry.register(new ConfigCommand());
        registry.register(new SessionCommand());
        registry.register(new HelpCommand());
        registry.register(new CompactCommand());
        registry.register(new ResetCommand());
        registry.register(new ToolsCommand());
        registry.register(new SkillsCommand());
        registry.register(new DoctorCommand());
        log.info("Registered {} builtin commands", registry.size());
    }

    /**
     * 处理输入文本 — 如果是斜杠命令则执行，否则返回 null。
     */
    public java.util.Optional<CommandResult> handleCommand(String rawText, CommandContext context) {
        var input = CommandInput.parse(rawText, context.session() != null ? context.session().key().toString() : "default", context.config().agent().modelId());
        if (!input.isCommand()) {
            return java.util.Optional.empty();
        }

        var result = registry.execute(input, context).block();
        return java.util.Optional.of(result);
    }

    /**
     * 获取命令注册表。
     */
    public CommandRegistry getRegistry() {
        return registry;
    }
}
