package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmRequest;
import com.openclaw.desktop.llm.LlmResponse;
import com.openclaw.desktop.llm.LlmEvent;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 子代理管理器 — spawning、监控和管理子代理。
 * 对应 OpenClaw 的 sub-agent 机制。
 *
 * <p>子代理运行在独立会话中，可以：
 * <ul>
 *   <li>使用不同的模型/配置</li>
 *   <li>继承父代理的工具集</li>
 *   <li>完成后将结果返回给父代理</li>
 * </ul>
 */
public class SubAgentManager {

    private static final Logger log = LoggerFactory.getLogger(SubAgentManager.class);
    private final SessionManager sessionManager;
    private final LlmProvider defaultProvider;
    private final ToolRegistry toolRegistry;
    private final ConcurrentHashMap<String, SubAgent> activeSubAgents = new ConcurrentHashMap<>();

    public SubAgentManager(SessionManager sessionManager, LlmProvider defaultProvider, ToolRegistry toolRegistry) {
        this.sessionManager = sessionManager;
        this.defaultProvider = defaultProvider;
        this.toolRegistry = toolRegistry;
    }

    /** 启动子代理 — 返回子代理 ID */
    public Mono<String> spawn(String task, SubAgentConfig config) {
        var subAgentId = "sub_" + UUID.randomUUID().toString().substring(0, 8);
        var sessionKey = SessionKey.subagent(config.parentAgentId(), subAgentId);

        log.info("Spawning sub-agent: id={}, taskLen={}, model={}", subAgentId, task.length(), config.modelId());

        return sessionManager.getOrCreate(sessionKey)
            .map(session -> {
                var agentConfig = new AgentConfig(
                    subAgentId, config.name(), config.modelId(),
                    config.systemPrompt(),
                    config.reasoningLevel(),
                    config.maxTokens(),
                    config.temperature(),
                    List.of(),
                    java.time.Instant.now()
                );

                var provider = config.providerId() != null
                    ? defaultProvider  // 简化：使用 defaultProvider
                    : defaultProvider;

                var agent = new Agent(agentConfig, provider, toolRegistry, session);
                var subAgent = new SubAgent(subAgentId, agent, session, config, SubAgentStatus.RUNNING);
                activeSubAgents.put(subAgentId, subAgent);

                return subAgentId;
            });
    }

    /** 启动子代理并等待完成 */
    public Mono<String> spawnAndWait(String task, SubAgentConfig config) {
        return spawn(task, config)
            .flatMap(id -> {
                var subAgent = activeSubAgents.get(id);
                if (subAgent == null) return Mono.just("Sub-agent not found");
                return subAgent.agent().chat(task)
                    .map(result -> {
                        subAgent.setStatus(SubAgentStatus.COMPLETED);
                        subAgent.setResult(result);
                        log.info("Sub-agent completed: id={}, resultLen={}", id, result.length());
                        return result;
                    });
            });
    }

    /** 流式子代理 — 返回文本块流 */
    public Flux<String> spawnStream(String task, SubAgentConfig config) {
        return spawn(task, config)
            .flatMapMany(id -> {
                var subAgent = activeSubAgents.get(id);
                if (subAgent == null) return Flux.<String>empty();
                return subAgent.agent().chatStream(task)
                    .doOnComplete(() -> {
                        subAgent.setStatus(SubAgentStatus.COMPLETED);
                    })
                    .doOnError(e -> {
                        subAgent.setStatus(SubAgentStatus.FAILED);
                        subAgent.setError(e.getMessage());
                    });
            });
    }

    /** 取消子代理 */
    public Mono<Void> cancel(String subAgentId) {
        return Mono.fromRunnable(() -> {
            var subAgent = activeSubAgents.get(subAgentId);
            if (subAgent != null) {
                subAgent.setStatus(SubAgentStatus.CANCELLED);
                log.info("Sub-agent cancelled: id={}", subAgentId);
            }
        });
    }

    /** 获取活跃子代理列表 */
    public List<SubAgent> listActive() {
        return activeSubAgents.values().stream()
            .filter(sa -> sa.status() == SubAgentStatus.RUNNING)
            .toList();
    }

    /** 获取所有子代理 */
    public List<SubAgent> listAll() {
        return List.copyOf(activeSubAgents.values());
    }

    /** 获取指定子代理 */
    public SubAgent get(String id) { return activeSubAgents.get(id); }

    // ========== 类型 ==========

    public enum SubAgentStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }

    public record SubAgentConfig(
        String parentAgentId,
        String name,
        String modelId,
        String systemPrompt,
        ReasoningLevel reasoningLevel,
        int maxTokens,
        double temperature,
        String providerId,
        boolean isolatedContext
    ) {
        public static SubAgentConfig defaults(String parentAgentId) {
            return new SubAgentConfig(parentAgentId, "SubAgent", "gpt-4o",
                "You are a sub-agent. Complete the assigned task efficiently.",
                ReasoningLevel.OFF, 4096, 0.7, null, true);
        }
    }

    public static class SubAgent {
        private final String id;
        private final Agent agent;
        private final Session session;
        private final SubAgentConfig config;
        private volatile SubAgentStatus status;
        private volatile String result;
        private volatile String error;

        public SubAgent(String id, Agent agent, Session session, SubAgentConfig config, SubAgentStatus status) {
            this.id = id;
            this.agent = agent;
            this.session = session;
            this.config = config;
            this.status = status;
        }

        public String id() { return id; }
        public Agent agent() { return agent; }
        public Session session() { return session; }
        public SubAgentConfig config() { return config; }
        public SubAgentStatus status() { return status; }
        public String result() { return result; }
        public String error() { return error; }

        void setStatus(SubAgentStatus s) { this.status = s; }
        void setResult(String r) { this.result = r; }
        void setError(String e) { this.error = e; }
    }
}
