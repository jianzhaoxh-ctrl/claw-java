package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmRequest;
import com.openclaw.desktop.llm.LlmResponse;
import com.openclaw.desktop.llm.LlmEvent;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.llm.MessageContent;
import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.llm.UsageInfo;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.ToolResult;
import com.openclaw.desktop.tool.ToolInput;
import com.openclaw.desktop.tool.ToolContext;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.context.AssembleResult;
import com.openclaw.desktop.context.CompactResult;
import com.openclaw.desktop.context.CompactConfig;
import com.openclaw.desktop.context.AssembleResult;
import com.openclaw.desktop.context.CompactConfig;
import com.openclaw.desktop.context.CompactResult;
import com.openclaw.desktop.context.ContextEngine;
import com.openclaw.desktop.context.ContextEngineConfig;
import com.openclaw.desktop.context.TokenEstimator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Agent 循环 — v2.0 重构。
 *
 * <p>对应 OpenClaw 的 agent-loop.ts，完整实现生命周期事件和回调机制：
 * <ul>
 *   <li>AgentStart → TurnStart → MessageStart → [TextStart/Delta/End...] → MessageEnd → TurnEnd → AgentEnd</li>
 *   <li>工具调用：ToolCallStart → ToolCallDelta → ToolCallEnd → ToolResult → 继续循环</li>
 *   <li>回调：shouldStopAfterTurn, prepareNextTurn, getSteeringMessages</li>
 * </ul>
 */
public class AgentLoop {

    private static final Logger log = LoggerFactory.getLogger(AgentLoop.class);

    private final AgentConfig config;
    private final LlmProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final Session session;
    private final List<Message> conversation;
    private volatile boolean running;
    private AbortSignal abortSignal = new AbortSignal();

    // ---- v2.0 回调配置 ----
    private Predicate<TurnContext> shouldStopAfterTurn = ctx -> false;
    private java.util.function.Supplier<Mono<List<Message>>> getSteeringMessages = () -> Mono.just(List.of());
    private java.util.function.Function<TurnContext, Mono<TurnSnapshot>> prepareNextTurn = ctx -> Mono.just(null);

    public AgentLoop(AgentConfig config, LlmProvider llmProvider, ToolRegistry toolRegistry, Session session) {
        this.config = config;
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.session = session;
        this.conversation = new ArrayList<>();
        this.conversation.add(new Message.SystemMessage(config.systemPrompt()));
    }

    // ---- 回调设置 ----

    public AgentLoop shouldStopAfterTurn(Predicate<TurnContext> fn) {
        this.shouldStopAfterTurn = fn;
        return this;
    }

    public AgentLoop getSteeringMessages(java.util.function.Supplier<Mono<List<Message>>> fn) {
        this.getSteeringMessages = fn;
        return this;
    }

    public AgentLoop prepareNextTurn(java.util.function.Function<TurnContext, Mono<TurnSnapshot>> fn) {
        this.prepareNextTurn = fn;
        return this;
    }

    public AgentLoop abortSignal(AbortSignal signal) {
        this.abortSignal = signal;
        return this;
    }

    public AbortSignal abortSignal() { return abortSignal; }

    // ---- 对话入口 ----

    /**
     * 同步聊天 — 返回完整响应文本。
     * 向后兼容旧 API。
     */
    public Mono<String> chat(String userMessage) {
        return Flux.from(chatStream(userMessage))
            .filter(e -> e instanceof AgentEvent.TextDelta)
            .map(e -> ((AgentEvent.TextDelta) e).delta())
            .reduce(new StringBuilder(), StringBuilder::append)
            .map(StringBuilder::toString);
    }

    /**
     * 流式聊天 — 返回完整生命周期事件流。
     */
    public Flux<AgentEvent> chatStream(String userMessage) {
        var userMsg = new Message.UserMessage(userMessage);
        return Mono.fromRunnable(() -> {
            conversation.add(userMsg);
            session.addUserMessage(userMessage);
        }).thenMany(Flux.defer(() -> runAgentLoop(userMsg)));
    }

    // ---- 核心循环 ----

    private Flux<AgentEvent> runAgentLoop(Message prompt) {
        var newMessages = new ArrayList<Message>();
        newMessages.add(prompt);

        return Flux.<AgentEvent>create(sink -> {
            sink.next(new AgentEvent.AgentStart());

            // 处理初始 prompt
            sink.next(new AgentEvent.TurnStart());
            sink.next(new AgentEvent.MessageStart(prompt));
            sink.next(new AgentEvent.MessageEnd(prompt));

            // 开始内层循环
            runInnerLoop(newMessages, sink)
                .doOnSuccess(v -> {
                    sink.next(new AgentEvent.AgentEnd(newMessages));
                    sink.complete();
                })
                .doOnError(e -> {
                    log.error("Agent loop error", e);
                    sink.next(new AgentEvent.Error(e));
                    sink.next(new AgentEvent.AgentEnd(newMessages));
                    sink.complete();
                })
                .subscribe();
        });
    }

    private Mono<Void> runInnerLoop(ArrayList<Message> newMessages, reactor.core.publisher.FluxSink<AgentEvent> sink) {
        return Mono.defer(() -> {
            // 检查 steering messages
            return getSteeringMessages.get()
                .flatMap(steeringMsgs -> {
                    if (!steeringMsgs.isEmpty()) {
                        for (var msg : steeringMsgs) {
                            sink.next(new AgentEvent.MessageStart(msg));
                            sink.next(new AgentEvent.MessageEnd(msg));
                            conversation.add(msg);
                            newMessages.add(msg);
                        }
                    }
                    return streamAssistantResponse(newMessages, sink);
                });
        });
    }

    private Mono<Void> streamAssistantResponse(ArrayList<Message> newMessages, reactor.core.publisher.FluxSink<AgentEvent> sink) {
        // 检查中断信号
        if (abortSignal.isAborted()) {
            log.info("Agent loop aborted: {}", abortSignal.abortReason());
            sink.next(new AgentEvent.Error(new RuntimeException("Aborted: " + abortSignal.abortReason())));
            return Mono.empty();
        }

        // 等待暂停恢复
        var resumeWait = abortSignal.waitForResume();
        
        var request = buildRequest();

        return resumeWait.thenMany(llmProvider.chatStream(request))
            .doOnNext(event -> {
                // 转发 LlmEvent → AgentEvent
                var agentEvent = mapLlmEvent(event);
                if (agentEvent != null) {
                    sink.next(agentEvent);
                }
            })
            .collectList()
            .flatMap(events -> {
                // 构造完整的 AssistantMessage
                var response = LlmEvent.toResponse(events);
                var assistantMsg = buildAssistantMessage(response);
                conversation.add(assistantMsg);
                newMessages.add(assistantMsg);
                session.addAssistantMessage(response.text());

                sink.next(new AgentEvent.MessageEnd(assistantMsg));

                // 检查是否有工具调用
                var toolCalls = response.toolCalls();
                if (toolCalls.isEmpty()) {
                    // 无工具调用 → 结束
                    sink.next(new AgentEvent.TurnEnd(assistantMsg, List.of()));

                    if (shouldStopAfterTurn.test(new TurnContext(assistantMsg, List.of(), null))) {
                        return Mono.<Void>empty();
                    }
                    return Mono.<Void>empty();
                }

                // 有工具调用 → 执行工具
                return executeToolCalls(toolCalls, newMessages, sink)
                    .flatMap(toolResults -> {
                        sink.next(new AgentEvent.TurnEnd(assistantMsg, toolResults));

                        if (shouldStopAfterTurn.test(new TurnContext(assistantMsg, toolResults, null))) {
                            return Mono.<Void>empty();
                        }

                        // prepareNextTurn 回调
                        return prepareNextTurn.apply(new TurnContext(assistantMsg, toolResults, null))
                            .then(Mono.<Void>empty())
                            .then(Mono.defer(() -> {
                                sink.next(new AgentEvent.TurnStart());
                                return runInnerLoop(newMessages, sink);
                            }));
                    });
            })
            .onErrorResume(e -> {
                log.error("Stream assistant response error", e);
                sink.next(new AgentEvent.Error(e));
                return Mono.empty();
            });
    }

    private AgentEvent mapLlmEvent(LlmEvent event) {
        return switch (event) {
            case LlmEvent.TextStart(var idx, var msg) -> new AgentEvent.TextStart(idx);
            case LlmEvent.TextDelta(var idx, var delta, var msg) -> new AgentEvent.TextDelta(idx, delta);
            case LlmEvent.TextEnd(var idx, var fullText, var msg) -> new AgentEvent.TextEnd(idx, fullText);
            case LlmEvent.ThinkingStart(var idx, var msg) -> new AgentEvent.ThinkingStart(idx);
            case LlmEvent.ThinkingDelta(var idx, var delta, var msg) -> new AgentEvent.ThinkingDelta(idx, delta);
            case LlmEvent.ThinkingEnd(var idx, var fullThinking, var msg) -> new AgentEvent.ThinkingEnd(idx, fullThinking);
            case LlmEvent.ToolCallStart(var idx, var name, var msg) -> new AgentEvent.ToolCallStart(idx, name);
            case LlmEvent.ToolCallDelta(var idx, var delta, var msg) -> new AgentEvent.ToolCallDelta(idx, delta);
            case LlmEvent.ToolCallEnd(var idx, var tc, var msg) -> new AgentEvent.ToolCallEnd(idx,
                tc != null ? tc.id() : "call_" + idx, tc != null ? tc.arguments() : "");
            case LlmEvent.Usage(var usage) -> new AgentEvent.Usage(usage);
            case LlmEvent.Error(var err, var msg) -> new AgentEvent.Error(err);
            // 生命周期事件由 AgentLoop 自身管理，不从 LlmEvent 映射
            default -> null;
        };
    }

    private Message.AssistantMessage buildAssistantMessage(LlmResponse response) {
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

    private Mono<List<Message>> executeToolCalls(
            List<MessageContent.ToolCallContent> toolCalls,
            ArrayList<Message> newMessages,
            reactor.core.publisher.FluxSink<AgentEvent> sink) {

        var results = new java.util.concurrent.CopyOnWriteArrayList<Message>();

        return Flux.fromIterable(toolCalls)
            .flatMap(tc -> {
                sink.next(new AgentEvent.ToolCallStart(0, tc.name()));
                return executeToolCall(tc)
                    .doOnNext(result -> {
                        sink.next(new AgentEvent.ToolCallEnd(0, tc.id(), result.text()));
                        results.add(result);
                        newMessages.add(result);
                    });
            })
            .then(Mono.just(List.copyOf(results)));
    }

    private Mono<Message.ToolResultMessage> executeToolCall(MessageContent.ToolCallContent tc) {
        log.info("Executing tool: {} (id={})", tc.name(), tc.id());
        var toolOpt = toolRegistry.get(tc.name());
        if (toolOpt.isEmpty()) {
            var msg = "Tool not found: " + tc.name();
            log.warn(msg);
            var result = new Message.ToolResultMessage(tc.id(), tc.name(),
                List.of(new MessageContent.TextContent(msg)), true, System.currentTimeMillis());
            conversation.add(result);
            return Mono.just(result);
        }
        var tool = toolOpt.get();
        var input = new ToolInput(tc.id(), tc.arguments());
        var ctx = new ToolContext(session, null, null, java.util.Map.of());

        return tool.execute(input, ctx)
            .map(toolResult -> {
                var result = new Message.ToolResultMessage(
                    tc.id(), tc.name(),
                    List.of(new MessageContent.TextContent(toolResult.content())),
                    !toolResult.success(),
                    System.currentTimeMillis()
                );
                conversation.add(result);
                return result;
            })
            .onErrorResume(e -> {
                var msg = "Tool error: " + e.getMessage();
                var result = new Message.ToolResultMessage(tc.id(), tc.name(),
                    List.of(new MessageContent.TextContent(msg)), true, System.currentTimeMillis());
                conversation.add(result);
                return Mono.just(result);
            });
    }

    private LlmRequest buildRequest() {
        var toolDescriptors = toolRegistry.listDescriptors();
        return new LlmRequest(
            config.modelId(),
            List.copyOf(conversation),
            config.temperature(),
            config.maxTokens(),
            config.reasoningLevel(),
            toolDescriptors,
            null,
            java.util.Map.of()
        );
    }

    // ---- 公开 API ----

    public List<Message> conversation() { return List.copyOf(conversation); }
    public boolean isRunning() { return running; }

    // ---- 回调上下文 ----

    /** 传递给回调的每轮上下文 */
    public record TurnContext(
        Message assistantMessage,
        List<Message> toolResults,
        Object context
    ) {}

    /** prepareNextTurn 回调返回的快照 */
    public record TurnSnapshot(
        Message context,
        String model,
        ReasoningLevel thinkingLevel
    ) {}
}
