package com.openclaw.desktop.infra.health;

/**
 * 健康检查接口 — 各子系统实现此接口供 Gateway 统一检查。
 */
public interface HealthCheck {
    String name();
    HealthCheckResult check();
}
