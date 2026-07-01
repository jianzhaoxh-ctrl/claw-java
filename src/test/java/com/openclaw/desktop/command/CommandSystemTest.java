package com.openclaw.desktop.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命令系统单元测试 — 覆盖 CommandInput 解析、CommandRegistry 注册和命令执行。
 */
class CommandSystemTest {

    private CommandRegistry registry;
    private CommandContext minimalContext;

    @BeforeEach
    void setup() {
        registry = new CommandRegistry();
        minimalContext = CommandContext.minimal();

        // 注册几个内置命令
        registry.register(new com.openclaw.desktop.command.impl.HelpCommand());
        registry.register(new com.openclaw.desktop.command.impl.StatusCommand());
        registry.register(new com.openclaw.desktop.command.impl.ToolsCommand());
        registry.register(new com.openclaw.desktop.command.impl.ResetCommand());
        registry.register(new com.openclaw.desktop.command.impl.DoctorCommand());
        registry.register(new com.openclaw.desktop.command.impl.CompactCommand());
        registry.register(new com.openclaw.desktop.command.impl.ConfigCommand());
        registry.register(new com.openclaw.desktop.command.impl.SessionCommand());
        registry.register(new com.openclaw.desktop.command.impl.ModelsCommand());
        registry.register(new com.openclaw.desktop.command.impl.SkillsCommand());
    }

    @Test
    void testCommandInputParse() {
        var input = CommandInput.parse("/status --verbose", "session1", "agent1");
        assertTrue(input.isCommand());
        assertEquals("status", input.commandName());
        assertTrue(input.hasOption("verbose"));
        assertEquals("session1", input.sessionId());
    }

    @Test
    void testCommandInputParseWithArgs() {
        var input = CommandInput.parse("/config set temperature 0.5", "s1", "a1");
        assertEquals("config", input.commandName());
        assertEquals(3, input.arguments().size());
        assertEquals("set", input.firstArg());
        assertEquals("temperature", input.arguments().get(1));
        assertEquals("0.5", input.arguments().get(2));
    }

    @Test
    void testCommandInputParseWithOptions() {
        var input = CommandInput.parse("/models --provider=openai", "s1", "a1");
        assertEquals("models", input.commandName());
        assertEquals("openai", input.option("provider"));
    }

    @Test
    void testCommandInputNotCommand() {
        var input = CommandInput.parse("Hello, how are you?", "s1", "a1");
        assertFalse(input.isCommand());
        assertEquals("", input.commandName());
    }

    @Test
    void testCommandInputEmpty() {
        var input = CommandInput.parse("", "s1", "a1");
        assertFalse(input.isCommand());
    }

    @Test
    void testRegistryRegister() {
        assertEquals(10, registry.size());
        assertTrue(registry.hasCommand("status"));
        assertTrue(registry.hasCommand("help"));
        assertTrue(registry.hasCommand("doctor"));
    }

    @Test
    void testRegistryUnregister() {
        registry.unregister("status");
        assertFalse(registry.hasCommand("status"));
        assertEquals(9, registry.size());
    }

    @Test
    void testHelpCommand() {
        var input = CommandInput.parse("/help", "s1", "a1");
        var result = registry.execute(input, minimalContext).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.output().contains("Available Commands"));
    }

    @Test
    void testDoctorCommand() {
        var input = CommandInput.parse("/doctor", "s1", "a1");
        var result = registry.execute(input, minimalContext).block();
        assertNotNull(result);
        assertTrue(result.success());
        assertTrue(result.output().contains("ClawDesktop Doctor"));
    }

    @Test
    void testUnknownCommand() {
        var input = CommandInput.parse("/unknown", "s1", "a1");
        var result = registry.execute(input, minimalContext).block();
        assertNotNull(result);
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Unknown command"));
    }

    @Test
    void testNonCommandText() {
        var input = CommandInput.parse("just chatting", "s1", "a1");
        var result = registry.execute(input, minimalContext).block();
        assertFalse(result.success());
        assertTrue(result.errorMessage().contains("Not a command"));
    }

    @Test
    void testCommandDefinition() {
        var def = CommandDefinition.query("test", "Test query", "/test");
        assertEquals("test", def.name());
        assertEquals(CommandResult.CommandType.QUERY, def.riskLevel());
        assertFalse(def.requiresApproval());

        var dangerous = CommandDefinition.dangerous("nuke", "Nuclear option", "/nuke");
        assertTrue(dangerous.requiresApproval());
        assertEquals(CommandResult.CommandType.DANGEROUS, dangerous.riskLevel());
    }

    @Test
    void testCommandResultFactory() {
        var ok = CommandResult.ok("Success!");
        assertTrue(ok.success());
        assertEquals("Success!", ok.output());
        assertNull(ok.errorMessage());

        var err = CommandResult.error("Something failed");
        assertFalse(err.success());
        assertEquals("Something failed", err.errorMessage());
    }

    @Test
    void testCommandNames() {
        var names = registry.commandNames();
        assertTrue(names.contains("status"));
        assertTrue(names.contains("help"));
        assertTrue(names.contains("doctor"));
    }

    @Test
    void testListDefinitions() {
        var defs = registry.listDefinitions().collectList().block();
        assertNotNull(defs);
        assertEquals(10, defs.size());
    }
}
