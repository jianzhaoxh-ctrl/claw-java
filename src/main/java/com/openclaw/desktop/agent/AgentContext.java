package com.openclaw.desktop.agent;

import com.openclaw.desktop.llm.LlmProvider;
import com.openclaw.desktop.llm.LlmProviderRegistry;
import com.openclaw.desktop.llm.Message;
import com.openclaw.desktop.memory.MemoryDatabase;
import com.openclaw.desktop.rag.SemanticSearch;
import com.openclaw.desktop.session.Session;
import com.openclaw.desktop.session.SessionKey;
import com.openclaw.desktop.session.SessionManager;
import com.openclaw.desktop.tool.ToolRegistry;
import com.openclaw.desktop.tool.ToolContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 上下文 — Agent 运行时的完整环境。
 * 对应 OpenClaw 的 AgentContext。
 *
 * <p>包含所有 Agent 运行需要的信息和服务：
 * <ul>
 *   <li>会话和会话管理器</li>
 *   <li>LLM Provider 注册表</li>
 *   <li>工具注册表</li>
 *   <li>记忆数据库</li>
 *   <li>语义搜索</li>
 *   <li>工作目录</li>
 *   <li>环境变量</li>
 *   <li>中断信号</li>
 * </ul>
 */
public class AgentContext {

    private final AgentConfig config;
    private final Session session;
    private final SessionManager sessionManager;
    private final LlmProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final MemoryDatabase memoryDatabase;
    private final SemanticSearch semanticSearch;
    private final Path workspaceDir;
    private final Map<String, String> env;
    private final AbortSignal abortSignal;

    public AgentContext(
        AgentConfig config,
        Session session,
        SessionManager sessionManager,
        LlmProviderRegistry providerRegistry,
        ToolRegistry toolRegistry,
        MemoryDatabase memoryDatabase,
        SemanticSearch semanticSearch,
        Path workspaceDir,
        Map<String, String> env,
        AbortSignal abortSignal
    ) {
        this.config = config;
        this.session = session;
        this.sessionManager = sessionManager;
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.memoryDatabase = memoryDatabase;
        this.semanticSearch = semanticSearch;
        this.workspaceDir = workspaceDir;
        this.env = env;
        this.abortSignal = abortSignal;
    }

    /** 快速构建 — 使用默认值 */
    public static AgentContext defaults(AgentConfig config, LlmProviderRegistry providerRegistry, ToolRegistry toolRegistry) {
        var sessionManager = new SessionManager();
        var session = sessionManager.getOrCreate(SessionKey.main("default")).block();
        try {
            var memoryDatabase = new MemoryDatabase("data/memory/claw.db");
            return new AgentContext(config, session, sessionManager, providerRegistry, toolRegistry,
                memoryDatabase, null, Path.of(System.getProperty("user.dir")), Map.of(), new AbortSignal());
        } catch (Exception e) {
            return new AgentContext(config, session, sessionManager, providerRegistry, toolRegistry,
                null, null, Path.of(System.getProperty("user.dir")), Map.of(), new AbortSignal());
        }
    }

    // ---- 公开 API ----

    public AgentConfig config() { return config; }
    public Session session() { return session; }
    public SessionManager sessionManager() { return sessionManager; }
    public LlmProviderRegistry providerRegistry() { return providerRegistry; }
    public ToolRegistry toolRegistry() { return toolRegistry; }
    public MemoryDatabase memoryDatabase() { return memoryDatabase; }
    public SemanticSearch semanticSearch() { return semanticSearch; }
    public Path workspaceDir() { return workspaceDir; }
    public Map<String, String> env() { return env; }
    public AbortSignal abortSignal() { return abortSignal; }

    /** 获取默认 LLM Provider */
    public Optional<LlmProvider> defaultProvider() {
        return Optional.ofNullable(providerRegistry.getDefault());
    }

    /** 获取指定 ID 的 Provider */
    public Optional<LlmProvider> getProvider(String id) {
        return providerRegistry.get(id);
    }

    /** 构建 ToolContext */
    public ToolContext createToolContext() {
        return new ToolContext(session, this, workspaceDir, env);
    }

    /** 搜索记忆 — 使用语义搜索或关键词搜索 */
    public List<Message> searchMemory(String query, int topK) {
        // 如果有语义搜索，优先使用
        if (semanticSearch != null) {
            try {
                var hits = semanticSearch.hybridSearch(query, topK, 0.7, 0.3)
                    .collectList().block();
                if (hits != null && !hits.isEmpty()) {
                    return hits.stream()
                        .<Message>map(hit -> (Message) new Message.SystemMessage(
                            "[Memory] " + hit.content() + " (score: " + String.format("%.2f", hit.score()) + ")"))
                        .toList();
                }
            } catch (Exception e) {
                // 语义搜索失败，尝试关键词搜索
            }
        }

        // 关键词搜索
        if (memoryDatabase != null) {
            try {
                var entries = memoryDatabase.search(query, topK);
                return entries.stream()
                    .<Message>map(entry -> (Message) new Message.SystemMessage(
                        "[Memory] " + entry.content()))
                    .toList();
            } catch (Exception e) {
                return List.of();
            }
        }

        return List.of();
    }
}
