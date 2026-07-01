package com.openclaw.desktop.types;

import java.util.Map;

/**
 * 不可变 JSON 值接口 — 对应 TypeScript 的 JsonValue。
 */
public sealed interface JsonValue {

    record JsonString(String value) implements JsonValue {}
    record JsonNumber(Number value) implements JsonValue {}
    record JsonBoolean(boolean value) implements JsonValue {}
    record JsonNull() implements JsonValue {}
    record JsonArray(java.util.List<JsonValue> items) implements JsonValue {}
    record JsonObject(Map<String, JsonValue> fields) implements JsonValue {}

    // -------- factory helpers --------

    static JsonValue of(String v) { return new JsonString(v); }
    static JsonValue of(Number v) { return new JsonNumber(v); }
    static JsonValue of(boolean v) { return new JsonBoolean(v); }
    static JsonValue ofNull() { return new JsonNull(); }
    static JsonValue ofArray(java.util.List<JsonValue> items) { return new JsonArray(items); }
    static JsonValue ofObject(Map<String, JsonValue> fields) { return new JsonObject(fields); }
}
