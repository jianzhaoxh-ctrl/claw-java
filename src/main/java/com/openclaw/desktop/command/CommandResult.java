package com.openclaw.desktop.command;

/**
 * 命令结果 — 命令执行后的返回值。
 */
public record CommandResult(
    boolean success,
    String output,
    String errorMessage,
    CommandType type
) {
    public static CommandResult ok(String output) {
        return new CommandResult(true, output, null, CommandType.INTERNAL);
    }

    public static CommandResult ok(String output, CommandType type) {
        return new CommandResult(true, output, null, type);
    }

    public static CommandResult error(String message) {
        return new CommandResult(false, null, message, CommandType.INTERNAL);
    }

    public static CommandResult error(String message, CommandType type) {
        return new CommandResult(false, null, message, type);
    }

    /**
     * 命令类型 — 区分命令对系统的影响级别。
     */
    public enum CommandType {
        /** 纯信息查询，不影响系统状态 */
        QUERY,
        /** 修改系统状态（配置、会话等） */
        MUTATION,
        /** 需要用户确认的危险操作 */
        DANGEROUS,
        /** 系统内部命令 */
        INTERNAL
    }
}
