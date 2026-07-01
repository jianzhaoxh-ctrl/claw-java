package com.openclaw.desktop.command;

import java.util.List;
import java.util.Map;

/**
 * 命令输入 — 用户发送的命令原始数据。
 */
public record CommandInput(
    String rawText,
    String commandName,
    List<String> arguments,
    Map<String, String> options,
    String sessionId,
    String agentId
) {
    /**
     * 从原始文本解析命令输入。
     * 格式：/commandName arg1 arg2 --option1=value1 --option2
     */
    public static CommandInput parse(String rawText, String sessionId, String agentId) {
        if (rawText == null || rawText.isBlank()) {
            return new CommandInput(rawText, "", List.of(), Map.of(), sessionId, agentId);
        }

        var trimmed = rawText.trim();
        if (!trimmed.startsWith("/")) {
            return new CommandInput(rawText, "", List.of(), Map.of(), sessionId, agentId);
        }

        var parts = trimmed.substring(1).split("\\s+");
        var commandName = parts[0];
        var arguments = new java.util.ArrayList<String>();
        var options = new java.util.LinkedHashMap<String, String>();

        for (int i = 1; i < parts.length; i++) {
            var part = parts[i];
            if (part.startsWith("--")) {
                var opt = part.substring(2);
                var eqIdx = opt.indexOf('=');
                if (eqIdx > 0) {
                    options.put(opt.substring(0, eqIdx), opt.substring(eqIdx + 1));
                } else {
                    options.put(opt, "true");
                }
            } else if (part.startsWith("-") && part.length() > 1 && !Character.isDigit(part.charAt(1))) {
                // 短选项 -x=value 或 -x
                var opt = part.substring(1);
                var eqIdx = opt.indexOf('=');
                if (eqIdx > 0) {
                    options.put(opt.substring(0, eqIdx), opt.substring(eqIdx + 1));
                } else {
                    options.put(opt, "true");
                }
            } else {
                arguments.add(part);
            }
        }

        return new CommandInput(rawText, commandName, List.copyOf(arguments), Map.copyOf(options), sessionId, agentId);
    }

    /**
     * 是否是命令（以 / 开头）。
     */
    public boolean isCommand() {
        return !commandName.isEmpty();
    }

    /**
     * 获取第一个参数。
     */
    public String firstArg() {
        return arguments.isEmpty() ? null : arguments.get(0);
    }

    /**
     * 获取选项值。
     */
    public String option(String key) {
        return options.get(key);
    }

    /**
     * 是否有指定选项。
     */
    public boolean hasOption(String key) {
        return options.containsKey(key);
    }
}
