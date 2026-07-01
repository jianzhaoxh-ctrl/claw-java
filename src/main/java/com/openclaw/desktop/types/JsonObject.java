package com.openclaw.desktop.types;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简单 JSON 对象包装 — 用于工具描述符中的 schema。
 * 底层用 Map 存储，序列化时由 Jackson 处理。
 */
public record JsonObject(Map<String, Object> value) {

    public static JsonObject empty() {
        return new JsonObject(Map.of());
    }

    public static JsonObject of(String key, Object value) {
        return new JsonObject(Map.of(key, value));
    }

    public static JsonObject of(String k1, Object v1, String k2, Object v2) {
        return new JsonObject(Map.of(k1, v1, k2, v2));
    }

    /**
     * 从 Jackson JsonNode 包装为 JsonObject。
     */
    public static JsonObject wrap(JsonNode node) {
        if (node == null || node.isNull()) return empty();
        var map = new LinkedHashMap<String, Object>();
        node.fields().forEachRemaining(entry -> {
            var n = entry.getValue();
            if (n.isObject()) map.put(entry.getKey(), wrap(n));
            else if (n.isArray()) {
                var list = new java.util.ArrayList<>();
                n.forEach(e -> list.add(e.isObject() ? wrap(e) : e.asText()));
                map.put(entry.getKey(), list);
            }
            else if (n.isNumber()) map.put(entry.getKey(), n.numberValue());
            else if (n.isBoolean()) map.put(entry.getKey(), n.booleanValue());
            else map.put(entry.getKey(), n.asText());
        });
        return new JsonObject(map);
    }

    public JsonObject merge(JsonObject other) {
        var merged = new LinkedHashMap<>(value);
        merged.putAll(other.value);
        return new JsonObject(Map.copyOf(merged));
    }
}
