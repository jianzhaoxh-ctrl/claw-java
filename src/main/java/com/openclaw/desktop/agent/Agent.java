package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmRequest;
import com.openclaw.desktop.llm.LlmResponse;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.session.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent — 封装 AI 对话循环，支持工具调用。
 *
 * <p>核心流程：
 * <ol>
 *   <li>构建消息列表（system prompt + 历史 + 用户消息）</li>
 *   <li>调用 LLM，携带可用工具列表</li>
 *   <li>如果 LLM 返回 tool_calls → 执行工具 → 把结果回传 → 再次调用 LLM</li>
 *   <li>重复 2-3 直到 LLM 不再请求工具（最多 5 轮）</li>
 * </ol>
 */
public class Agent {

    private static final Logger log = LoggerFactory.getLogger(Agent.class);
    private static final int MAX_TOOL_ROUNDS = 20;

    private final AgentConfig config;
    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final Session session;
    private AgentLoop loop;
    private AgentState state;

    public Agent(AgentConfig config, LlmProvider llmProvider, ToolRegistry toolRegistry, Session session) {
        this.config = config;
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.session = session;
        this.state = AgentState.initial();
    }

    // ==================== 同步聊天（带工具调用） ====================

    /**
     * 同步聊天 — 传工具给 LLM，支持多轮工具调用。
     */
    public Mono<String> chat(String message) {
        log.info("Agent chat: model={}, messageLen={}", config.modelId(), message.length());

        var messages = buildMessages(message);

        return chatWithTools(messages, 0)
            .doOnNext(resp -> {
                session.addUserMessage(message);
                session.addAssistantMessage(resp);
                // 持久化
                try {
                    new com.openclaw.desktop.session.SessionStore().save(session);
                } catch (Exception e) {
                    log.warn("Failed to persist session: {}", e.getMessage());
                }
            })
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 带工具的递归对话循环。
     */
    private Mono<String> chatWithTools(List<Message> messages, int round) {
        if (round >= MAX_TOOL_ROUNDS) {
            log.warn("Tool round limit reached ({})", MAX_TOOL_ROUNDS);
            // 超限时，不报错，发送一个不带 tools 的请求让 LLM 总结已有结果
            var summaryRequest = new LlmRequest(
                config.modelId(), messages,
                config.temperature(), Math.max(config.maxTokens(), 4096),
                config.reasoningLevel(),
                List.of(), // 不传 tools，强制 LLM 给出文本回复
                null, Map.of()
            );
            return llmProvider.chat(summaryRequest)
                .map(LlmResponse::text)
                .onErrorResume(e -> Mono.just("已完成多轮工具调用，但总结时出错：" + e.getMessage()));
        }

        // 默认始终传 tools，让 LLM 自主决定是否调用
        var tools = toolRegistry.listDescriptors();
        log.debug("Round {}: {} tools available", round, tools.size());

        var request = new LlmRequest(
            config.modelId(),
            messages,
            config.temperature(),
            Math.max(config.maxTokens(), 4096),
            config.reasoningLevel(),
            tools,
            null,
            Map.of()
        );

        return llmProvider.chat(request)
            .flatMap(response -> {
                var toolCalls = response.toolCalls();
                var text = response.text();

                log.info("Round {}: respLen={}, toolCalls={}", round, text.length(), toolCalls.size());

                if (toolCalls.isEmpty()) {
                    // 无工具调用 → 直接返回文本
                    return Mono.just(text);
                }

                // 有工具调用 → 执行工具
                log.info("Executing {} tool call(s)", toolCalls.size());

                // 先把 AssistantMessage（含 tool_calls）加入对话
                messages.add(toAssistantMessage(response));

                // 逐个执行工具
                return executeToolCalls(toolCalls, messages)
                    .then(Mono.defer(() -> chatWithTools(messages, round + 1)));
            })
            .onErrorResume(e -> {
                log.error("Agent chat error at round {}", round, e);
                return Mono.just("⚠ 请求失败：" + e.getMessage());
            });
    }

    // ==================== 工具执行 ====================

    /**
     * 执行一批工具调用，把结果作为 ToolResultMessage 加入 messages。
     */
    private Mono<Void> executeToolCalls(
            List<MessageContent.ToolCallContent> toolCalls,
            List<Message> messages) {

        return Flux.fromIterable(toolCalls)
            .concatMap(tc -> {
                log.info("  Tool: {} (id={}, args={})", tc.name(), tc.id(),
                    tc.arguments().length() > 200 ? tc.arguments().substring(0, 200) + "..." : tc.arguments());

                return executeSingleTool(tc)
                    .doOnNext(resultMsg -> {
                        messages.add(resultMsg);
                        log.info("  Tool {} done → {} chars", tc.name(),
                            MessageContent.extractText(resultMsg.content()).length());
                    });
            })
            .then();
    }

    /**
     * 执行单个工具调用（含审批检查）。
     */
    private Mono<Message.ToolResultMessage> executeSingleTool(MessageContent.ToolCallContent tc) {
        // 审批检查
        if (approvalManager != null && approvalManager.needsApproval(tc.name(), tc.arguments())) {
            var request = com.openclaw.desktop.approval.ApprovalRequest.of(tc.name(),
                tc.name(),
                "参数: " + (tc.arguments().length() > 100 ? tc.arguments().substring(0, 100) + "..." : tc.arguments()),
                com.openclaw.desktop.approval.ApprovalRequest.ApprovalLevel.NORMAL);

            log.info("Tool {} requires approval", tc.name());
            return approvalManager.requestApproval(request)
                .flatMap(result -> {
                    if (result.decision() != com.openclaw.desktop.approval.ApprovalResult.ApprovalDecision.APPROVED) {
                        log.info("Tool {} denied by user", tc.name());
                        var msg = new Message.ToolResultMessage(
                            tc.id(), tc.name(),
                            List.of(new MessageContent.TextContent("用户拒绝了此操作")),
                            true, System.currentTimeMillis()
                        );
                        return Mono.just(msg);
                    }
                    log.info("Tool {} approved", tc.name());
                    return doExecuteTool(tc);
                });
        }
        return doExecuteTool(tc);
    }

    /**
     * 实际执行工具（不含审批）。
     */
    private Mono<Message.ToolResultMessage> doExecuteTool(MessageContent.ToolCallContent tc) {
        var toolOpt = toolRegistry.get(tc.name());
        if (toolOpt.isEmpty()) {
            log.warn("  Tool not found: {}", tc.name());
            var msg = new Message.ToolResultMessage(
                tc.id(), tc.name(),
                List.of(new MessageContent.TextContent("Error: Tool '" + tc.name() + "' not found")),
                true, System.currentTimeMillis()
            );
            return Mono.just(msg);
        }

        var tool = toolOpt.get();
        var input = new ToolInput(tc.id(), tc.arguments());
        var ctx = new ToolContext(session, null, null, Map.of());

        return tool.execute(input, ctx)
            .map((ToolResult toolResult) -> {
                var resultMsg = new Message.ToolResultMessage(
                    tc.id(), tc.name(),
                    List.of(new MessageContent.TextContent(toolResult.content())),
                    !toolResult.success(),
                    System.currentTimeMillis()
                );
                return resultMsg;
            })
            .onErrorResume(e -> {
                log.error("  Tool {} failed", tc.name(), e);
                var resultMsg = new Message.ToolResultMessage(
                    tc.id(), tc.name(),
                    List.of(new MessageContent.TextContent("Tool error: " + e.getMessage())),
                    true, System.currentTimeMillis()
                );
                return Mono.just(resultMsg);
            });
    }

    // ==================== 流式聊天 ====================

    /**
     * 流式聊天 — 实时推送工具调用状态和最终文本。
     * 在工具调用过程中，会先推送状态信息（如"🔎 正在读取文件..."），
     * 让用户看到 AI 正在工作。
     */
    public Flux<String> chatStream(String message) {
        var messages = buildMessages(message);
        return chatWithToolsStream(messages, 0)
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 带实时状态推送的工具调用循环。
     */
    private Flux<String> chatWithToolsStream(List<Message> messages, int round) {
        if (round >= MAX_TOOL_ROUNDS) {
            return Flux.just("\n⏳ 已达到工具调用上限，正在总结...\n\n");
        }

        var tools = toolRegistry.listDescriptors();
        var request = new LlmRequest(
            config.modelId(), messages,
            config.temperature(), Math.max(config.maxTokens(), 4096),
            config.reasoningLevel(), tools, null, Map.of()
        );

        return llmProvider.chat(request)
            .flatMapMany(response -> {
                var toolCalls = response.toolCalls();
                var text = response.text();

                if (toolCalls.isEmpty()) {
                    // 最终回复
                    session.addUserMessage(messages.get(messages.size() - 1) instanceof Message.UserMessage um
                        ? com.openclaw.desktop.llm.MessageContent.extractText(um.content()) : "");
                    session.addAssistantMessage(text);
                    return Flux.just(text);
                }

                // 有工具调用 — 先推送状态文本
                var statusFlux = Flux.<String>create(sink -> {
                    for (var tc : toolCalls) {
                        var icon = switch (tc.name()) {
                            case "read_file", "read" -> "📖";
                            case "write_file", "write" -> "✍";
                            case "list_files", "list" -> "📂";
                            case "edit_file", "edit" -> "🔧";
                            case "delete_file", "delete" -> "🗑";
                            case "web_search", "search" -> "🔎";
                            case "exec", "execute" -> "⚙";
                            default -> "🔨";
                        };
                        // 解析参数中的路径或查询
                        var arg = tc.arguments();
                        var hint = "";
                        if (arg.contains("path")) {
                            try {
                                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(arg);
                                hint = node.path("path").asText("");
                            } catch (Exception ignored) {}
                        } else if (arg.contains("query")) {
                            try {
                                var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(arg);
                                hint = node.path("query").asText("");
                            } catch (Exception ignored) {}
                        }
                        if (hint.length() > 60) hint = "..." + hint.substring(hint.length() - 60);

                        sink.next(icon + " " + tc.name() + (hint.isEmpty() ? "..." : " → " + hint) + "\n\n");
                    }
                    sink.complete();
                });

                messages.add(toAssistantMessage(response));

                // 执行工具
                var toolFlux = executeToolCalls(toolCalls, messages)
                    .thenMany(Flux.<String>empty());

                // 状态推送 → 执行工具 → 继续下一轮
                return statusFlux
                    .concatWith(toolFlux)
                    .concatWith(Flux.defer(() -> chatWithToolsStream(messages, round + 1)));
            })
            .onErrorResume(e -> {
                log.error("Agent chatStream error at round {}", round, e);
                return Flux.just("\n\n⚠ " + (e.getMessage() != null ? e.getMessage() : "error") + "\n");
            });
    }

    // ==================== 消息构建 ====================

    /**
     * 构建消息列表：system + 历史 + 用户消息。
     */
    private List<Message> buildMessages(String userMessage) {
        var messages = new ArrayList<Message>();

        // 1. System prompt
        messages.add(new Message.SystemMessage(buildSystemPrompt()));

        // 2. 历史对话（最多 20 条）
        var entries = session.transcript().entries();
        var startIdx = Math.max(0, entries.size() - 20);
        for (int i = startIdx; i < entries.size(); i++) {
            var entry = entries.get(i);
            switch (entry.role()) {
                case "user" -> messages.add(new Message.UserMessage(
                    entry.content() != null ? entry.content() : ""));
                case "assistant" -> messages.add(new Message.AssistantMessage(
                    entry.content() != null ? entry.content() : "", List.of()));
            }
        }

        // 3. 当前用户消息
        messages.add(new Message.UserMessage(userMessage));

        return messages;
    }

    /**
     * 构建 System Prompt — 定义角色、能力、可用工具。
     */
    private String buildSystemPrompt() {
        // 动态列出可用工具
        var toolList = toolRegistry.listDescriptors();
        var toolDesc = new StringBuilder();
        for (var t : toolList) {
            toolDesc.append("- **").append(t.name()).append("**: ");
            toolDesc.append(t.description() != null ? t.description() : "No description");
            toolDesc.append("\n");
        }

        return """
            你是 ClawDesktop，一个强大的桌面 AI 助手。你拥有工具，必须积极使用它们。

            ## \u26a0\u26a0\u26a0 \u6700\u91cd\u8981\u7684\u89c4\u5219\u26a0\u26a0\u26a0
            - \u7528\u6237\u8ba9\u4f60\u7406\u89e3\u9879\u76ee/\u4ee3\u7801/\u6587\u4ef6\u65f6\uff0c\u7acb\u5373\u4f7f\u7528 list_files \u548c read_file \u5de5\u5177\u53bb\u8bfb\uff0c\u4e0d\u8981\u53ea\u662f\u201c\u8bf4\u201d\u8981\u8bfb\uff0c\u76f4\u63a5\u53bb\u8bfb\uff01
            - \u7528\u6237\u8ba9\u4f60\u641c\u7d22\u65f6\uff0c\u7acb\u5373\u4f7f\u7528 web_search \u5de5\u5177\u3002
            - \u7528\u6237\u8ba9\u4f60\u5199\u6587\u4ef6\u65f6\uff0c\u7acb\u5373\u4f7f\u7528 write_file \u5de5\u5177\u3002
            - \u5de5\u5177\u8c03\u7528\u540e\uff0c\u57fa\u4e8e\u8fd4\u56de\u7ed3\u679c\u7ee7\u7eed\u5de5\u4f5c\uff0c\u4e0d\u8981\u91cd\u590d\u63cf\u8ff0\u5de5\u5177\u8fd4\u56de\u7684\u5185\u5bb9\u3002
            - \u5148\u884c\u52a8\uff0c\u518d\u603b\u7ed3\u3002\u4e0d\u8981\u5148\u8bf4\u8ba1\u5212\u518d\u505a\u3002

            ## \u884c\u4e3a\u51c6\u5219
            - \u9ed8\u8ba4\u7528\u4e2d\u6587\u56de\u590d\n            - \u56de\u590d\u7b80\u6d01\u6709\u7528\uff0c\u4e0d\u8981\u5e9f\u8bdd
            - \u4f7f\u7528 Markdown \u683c\u5f0f\u5316
            - \u5982\u679c\u4e0d\u786e\u5b9a\uff0c\u8bda\u5b9e\u8bf4\n
            ## \u53ef\u7528\u5de5\u5177
            """ + toolDesc + """

            ## \u5f53\u524d\u73af\u5883
            - \u64cd\u4f5c\u7cfb\u7edf\uff1aWindows / \u4fe1\u521b OS
            - \u8fd0\u884c\u65f6\uff1aJava 21 + JavaFX 21
            """;
    }

    /**
     * 把 LlmResponse 转为 AssistantMessage。
     */
    private Message.AssistantMessage toAssistantMessage(LlmResponse response) {
        // 使用 content（List<MessageContent>）版本构造器
        return new Message.AssistantMessage(
            response.content(),
            response.api(),
            response.provider(),
            response.model(),
            response.responseModel(),
            response.responseId(),
            response.usage(),
            response.stopReason(),
            response.errorMessage(),
            System.currentTimeMillis()
        );
    }

    // ==================== 工具审批 ====================

    private com.openclaw.desktop.approval.ApprovalManager approvalManager;

    /** 设置审批管理器 */
    public void setApprovalManager(com.openclaw.desktop.approval.ApprovalManager am) {
        this.approvalManager = am;
    }

    // ==================== 其他 ====================

    public Mono<Void> reset() {
        log.info("Agent reset");
        this.loop = null;
        this.state = AgentState.initial();
        return Mono.empty();
    }

    public AgentConfig config() { return config; }
    public AgentState state() { return state; }
    public Session session() { return session; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public LlmProvider llmProvider() { return llmProvider; }
}
