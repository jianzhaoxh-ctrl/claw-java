package com.openclaw.desktop.plugin.example;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.plugin.ClawPlugin;
import com.openclaw.desktop.plugin.PluginContext;
import com.openclaw.desktop.plugin.PluginEvent;
import com.openclaw.desktop.plugin.EventBus;
import com.openclaw.desktop.tool.Tool;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.types.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * 示例内置插件 — Echo。
 *
 * <p>演示插件系统能力：
 * <ul>
 *   <li>注册一个 {@code echo} 工具（原样回显输入文本）</li>
 *   <li>订阅 {@link PluginEvent.PluginLoaded} 事件，记录其他插件的加载</li>
 * </ul>
 *
 * <p>该插件通过 SPI（META-INF/services）自动发现，无需手动注册。
 */
public class EchoPlugin implements ClawPlugin {

    private static final Logger log = LoggerFactory.getLogger(EchoPlugin.class);

    private EventBus.Subscription subscription;
    private String selfId;

    @Override
    public String id() { return "echo"; }

    @Override
    public String name() { return "Echo Plugin"; }

    @Override
    public String version() { return "0.1.0"; }

    @Override
    public String description() {
        return "示例插件：注册一个回显工具，并订阅插件生命周期事件。";
    }

    @Override
    public void init(PluginContext context) throws Exception {
        this.selfId = context.pluginId();
        // 1. 注册 echo 工具
        context.toolRegistry().register(new EchoTool(context));
        context.eventBus().publish(new PluginEvent.ToolRegistered("echo", selfId));
        log.info("EchoPlugin 注册了 echo 工具");

        // 2. 订阅插件加载事件（忽略自身）
        subscription = context.eventBus().subscribe(PluginEvent.PluginLoaded.class, e -> {
            if (!e.pluginId().equals(selfId)) {
                log.info("EchoPlugin 观察到插件加载: {} ({})", e.pluginName(), e.pluginId());
            }
        });
    }

    @Override
    public void destroy() {
        if (subscription != null) {
            subscription.cancel();
            subscription = null;
        }
        log.info("EchoPlugin 已销毁");
    }

    /** Echo 工具实现 — 原样回显输入文本。 */
    static class EchoTool implements Tool {
        private final PluginContext context;

        EchoTool(PluginContext context) { this.context = context; }

        @Override
        public ToolDescriptor descriptor() {
            var inputSchema = new JsonObject(Map.of(
                "type", "object",
                "properties", Map.of(
                    "text", Map.of("type", "string", "description", "要回显的文本")
                ),
                "required", java.util.List.of("text")
            ));
            return new ToolDescriptor(
                "echo",
                "Echo",
                "原样回显输入文本（示例插件工具）",
                inputSchema,
                JsonObject.empty()
            );
        }

        @Override
        public Mono<ToolResult> execute(ToolInput input, ToolContext ctx) {
            return Mono.fromCallable(() -> {
                var args = new com.fasterxml.jackson.databind.ObjectMapper().readTree(input.arguments());
                var text = args.has("text") ? args.get("text").asText() : "";
                // 发布事件通知
                context.eventBus().publish(new PluginEvent.CustomEvent(
                    "echo.called", text, context.pluginId()
                ));
                return ToolResult.success(input.toolCallId(), "Echo: " + text);
            });
        }
    }
}
