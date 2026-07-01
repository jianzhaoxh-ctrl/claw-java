package com.openclaw.desktop.command;

/**
 * 命令定义 — 描述一个斜杠命令的元数据。
 */
public record CommandDefinition(
    String name,
    String description,
    String usage,
    CommandResult.CommandType riskLevel,
    boolean requiresApproval,
    String[] aliases
) {
    /**
     * 创建一个安全的信息查询命令。
     */
    public static CommandDefinition query(String name, String description, String usage) {
        return new CommandDefinition(name, description, usage, CommandResult.CommandType.QUERY, false, new String[0]);
    }

    /**
     * 创建一个状态修改命令。
     */
    public static CommandDefinition mutation(String name, String description, String usage) {
        return new CommandDefinition(name, description, usage, CommandResult.CommandType.MUTATION, false, new String[0]);
    }

    /**
     * 创建一个危险命令（需要用户确认）。
     */
    public static CommandDefinition dangerous(String name, String description, String usage) {
        return new CommandDefinition(name, description, usage, CommandResult.CommandType.DANGEROUS, true, new String[0]);
    }

    /**
     * 创建带别名的命令。
     */
    public static CommandDefinition withAliases(String name, String description, String usage,
                                                  CommandResult.CommandType riskLevel, String... aliases) {
        return new CommandDefinition(name, description, usage, riskLevel, riskLevel == CommandResult.CommandType.DANGEROUS, aliases);
    }
}
