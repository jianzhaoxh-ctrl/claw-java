package com.openclaw.desktop.gateway.api;

import com.openclaw.desktop.gateway.server.GatewayRouter;
import com.openclaw.desktop.tool.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/**
 * 工具 API — GET /v1/tools, GET /v1/tools/{name}
 */
public class ToolApi {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ToolRegistry toolRegistry;

    public ToolApi() {
        this.toolRegistry = null;
    }

    public ToolApi(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public Mono<Void> listTools(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        return Mono.fromCallable(() -> {
            var arr = MAPPER.createArrayNode();
            if (toolRegistry != null) {
                for (var desc : toolRegistry.listDescriptors()) {
                    var obj = MAPPER.createObjectNode();
                    obj.put("name", desc.name());
                    obj.put("title", desc.title());
                    obj.put("description", desc.description());
                    arr.add(obj);
                }
            }
            return MAPPER.writeValueAsString(arr);
        }).flatMap(json -> res
            .header("Content-Type", "application/json")
            .sendString(Mono.just(json))
            .then());
    }

    public Mono<Void> getTool(HttpServerRequest req, HttpServerResponse res) {
        GatewayRouter.addCorsHeaders(res);
        var name = req.param("name");
        return Mono.fromCallable(() -> {
            if (toolRegistry == null) {
                return "{\"error\":\"Tool registry not initialized\"}";
            }
            var toolOpt = toolRegistry.get(name);
            if (toolOpt.isEmpty()) {
                return "{\"error\":\"Tool not found: " + name + "\"}";
            }
            var desc = toolOpt.get().descriptor();
            var obj = MAPPER.createObjectNode();
            obj.put("name", desc.name());
            obj.put("title", desc.title());
            obj.put("description", desc.description());
            return MAPPER.writeValueAsString(obj);
        }).flatMap(json -> res
            .header("Content-Type", "application/json")
            .sendString(Mono.just(json))
            .then());
    }
}
