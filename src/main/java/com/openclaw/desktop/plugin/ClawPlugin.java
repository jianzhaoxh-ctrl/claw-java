package com.openclaw.desktop.plugin;

/**
 * ClawDesktop 插件接口 — 所有插件必须实现此接口。
 * 使用 Java SPI (ServiceLoader) 机制发现和加载插件。
 * 对应 OpenClaw 的 plugin 系统。
 */
public interface ClawPlugin {

    /**
     * 插件唯一标识。
     */
    String id();

    /**
     * 插件显示名称。
     */
    String name();

    /**
     * 插件版本。
     */
    String version();

    /**
     * 插件描述。
     */
    String description();

    /**
     * 初始化插件 — 在 ClawDesktop 启动时调用。
     */
    void init(PluginContext context) throws Exception;

    /**
     * 销毁插件 — 在 ClawDesktop 关闭时调用。
     */
    default void destroy() {}
}
