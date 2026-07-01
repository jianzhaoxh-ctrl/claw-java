package com.openclaw.desktop.mcp;

import com.openclaw.desktop.llm.ToolDescriptor;
import com.openclaw.desktop.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端管理器 — 管理多个 MCP 服务器连接，发现工具并桥接到 {@link ToolRegistry}。
 *
 * <p>对应 OpenClaw 的 MCP 支持。负责：
 * <ul>
 *   <li>注册 MCP 服务器配置（{@link #registerServer}）</li>
 *   <li>连接所有服务器并发现工具（{@link #connectAll}）</li>
 *   <li>将 MCP 工具适配为 {@link McpToolAdapter} 注册到 ToolRegistry</li>
 *   <li>断开连接时注销工具（{@link #disconnectAll}）</li>
 * </ul>
 *
 * <p>当前仅支持 <b>stdio</b> 传输（启动子进程）。SSE 传输待后续实现。
 */
public class McpClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final Map<String, McpServerConfig> serverConfigs = new ConcurrentHashMap<>();
    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private final Map<String, List<McpToolAdapter>> toolAdapters = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;

    /**
     * @param toolRegistry 工具注册表（可为 null，仅管理连接不自动注册工具）
     */
    public McpClientManager(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public McpClientManager() {
        this(null);
    }

    /**
     * 注册一个 MCP 服务器配置（尚未连接）。
     */
    public void registerServer(McpServerConfig config) {
        serverConfigs.put(config.name(), config);
        log.info("MCP server config registered: {} (enabled={}, transport={})",
            config.name(), config.enabled(),
            config.url() != null ? "sse" : "stdio");
    }

    /**
     * 批量注册服务器配置。
     */
    public void registerServers(Collection<McpServerConfig> configs) {
        for (var c : configs) registerServer(c);
    }

    /**
     * 连接所有已注册且 enabled 的 MCP 服务器。
     */
    public void connectAll() {
        for (var config : serverConfigs.values()) {
            if (!config.enabled()) continue;
            try {
                connect(config.name());
            } catch (Exception e) {
                log.error("Failed to connect MCP server '{}': {}", config.name(), e.getMessage(), e);
            }
        }
    }

    /**
     * 连接指定 MCP 服务器，发现工具并注册到 ToolRegistry。
     */
    public synchronized void connect(String name) throws Exception {
        if (clients.containsKey(name)) {
            log.warn("MCP server '{}' already connected", name);
            return;
        }
        var config = serverConfigs.get(name);
        if (config == null) {
            throw new IllegalArgumentException("Unknown MCP server: " + name);
        }

        // SSE 传输暂不支持
        if (config.url() != null && config.command() == null) {
            throw new UnsupportedOperationException(
                "MCP SSE transport not yet implemented for server: " + name);
        }

        var client = new McpClient(config);
        client.start();
        clients.put(name, client);

        // 发现工具
        var tools = client.listTools();
        var adapters = new ArrayList<McpToolAdapter>();
        for (var tool : tools) {
            var adapter = new McpToolAdapter(client, tool);
            adapters.add(adapter);
            if (toolRegistry != null) {
                toolRegistry.register(adapter);
            }
        }
        toolAdapters.put(name, adapters);
        log.info("MCP server '{}' connected: {} tool(s) discovered and registered", name, tools.size());
    }

    /**
     * 断开指定 MCP 服务器，注销其所有工具。
     */
    public synchronized void disconnect(String name) {
        var client = clients.remove(name);
        var adapters = toolAdapters.remove(name);
        // 先注销工具，再关闭连接
        if (adapters != null && toolRegistry != null) {
            for (var adapter : adapters) {
                toolRegistry.unregister(adapter.descriptor().name());
            }
        }
        if (client != null) {
            client.close();
        }
        log.info("MCP server '{}' disconnected ({} tool(s) unregistered)", name,
            adapters != null ? adapters.size() : 0);
    }

    /**
     * 断开所有 MCP 服务器连接。
     */
    public synchronized void disconnectAll() {
        for (var name : new ArrayList<>(clients.keySet())) {
            disconnect(name);
        }
    }

    /**
     * 重新连接指定服务器（断开后重连，刷新工具列表）。
     */
    public synchronized void reconnect(String name) throws Exception {
        disconnect(name);
        connect(name);
    }

    // ---- 查询 ----

    /** 所有已连接服务器提供的工具描述符。 */
    public List<ToolDescriptor> allToolDescriptors() {
        return toolAdapters.values().stream()
            .flatMap(List::stream)
            .map(McpToolAdapter::descriptor)
            .toList();
    }

    /** 所有已连接服务器提供的原始 MCP 工具。 */
    public List<McpTool> allTools() {
        return toolAdapters.values().stream()
            .flatMap(List::stream)
            .map(McpToolAdapter::mcpTool)
            .toList();
    }

    /** 指定服务器的工具。 */
    public List<McpTool> tools(String serverName) {
        var adapters = toolAdapters.get(serverName);
        if (adapters == null) return List.of();
        return adapters.stream().map(McpToolAdapter::mcpTool).toList();
    }

    /** 所有已连接的服务器名。 */
    public Set<String> connectedServers() {
        return Collections.unmodifiableSet(clients.keySet());
    }

    /** 是否已连接。 */
    public boolean isConnected(String name) {
        var c = clients.get(name);
        return c != null && c.isRunning();
    }

    /** 已注册的服务器配置数。 */
    public int registeredCount() {
        return serverConfigs.size();
    }

    /** 已连接的服务器数。 */
    public int connectedCount() {
        return clients.size();
    }

    /** 获取指定服务器的客户端（用于直接调用）。 */
    public Optional<McpClient> client(String name) {
        return Optional.ofNullable(clients.get(name));
    }
}
