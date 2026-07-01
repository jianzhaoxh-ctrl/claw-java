package com.openclaw.desktop.command.impl;

import com.openclaw.desktop.command.*;
import reactor.core.publisher.Mono;

import java.util.ArrayList;

/**
 * /doctor 命令 — 运行诊断检查。
 * 对应 OpenClaw 的 `openclaw doctor` CLI 命令。
 */
public class DoctorCommand implements CommandHandler {
    @Override
    public CommandDefinition definition() {
        return CommandDefinition.query("doctor", "Run diagnostic checks", "/doctor [--verbose] [--fix]");
    }

    @Override
    public Mono<CommandResult> execute(CommandInput input, CommandContext context) {
        var verbose = input.hasOption("verbose") || input.hasOption("v");
        var fix = input.hasOption("fix");
        var sb = new StringBuilder("=== ClawDesktop Doctor ===\n\n");

        // 1. Java Environment
        sb.append("✅ Java: ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.home")).append(")\n");
        sb.append("✅ OS: ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.version")).append("\n");
        sb.append("✅ Encoding: ").append(System.getProperty("file.encoding")).append("\n\n");

        // 2. Provider checks
        var issues = new ArrayList<String>();
        if (context.providerRegistry() != null) {
            var providers = context.providerRegistry().all().stream().toList();
            sb.append("Providers (" + providers.size() + "):\n");
            for (var p : providers) {
                var health = p.healthCheck().block();
                sb.append(health ? "  ✅ " : "  ❌ ").append(p.name()).append(" — ").append(p.id()).append("\n");
                if (!health) {
                    issues.add("Provider " + p.name() + " health check failed");
                }
            }
        } else {
            sb.append("❌ No provider registry\n");
            issues.add("No provider registry configured");
        }

        // 3. Tool checks
        if (context.toolRegistry() != null) {
            sb.append("\nTools: ").append(context.toolRegistry().size()).append(" registered ✅\n");
        } else {
            sb.append("\n❌ No tool registry\n");
            issues.add("No tool registry available");
        }

        // 4. Memory checks
        if (context.memoryDatabase() != null) {
            sb.append("Memory DB: available ✅\n");
        } else {
            sb.append("Memory DB: not configured ⚠\n");
            if (verbose) issues.add("Memory database not configured");
        }

        // 5. Summary
        sb.append("\n--- Summary ---\n");
        if (issues.isEmpty()) {
            sb.append("✅ No issues found — system healthy\n");
        } else {
            sb.append("⚠ " + issues.size() + " issue(s):\n");
            for (var issue : issues) {
                sb.append("  • ").append(issue).append("\n");
            }
            if (fix) {
                sb.append("\nAuto-fix not yet implemented. Please configure providers manually.\n");
            }
        }

        return Mono.just(CommandResult.ok(sb.toString()));
    }
}
