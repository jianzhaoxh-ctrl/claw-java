package com.openclaw.desktop.infra.health;

/**
 * 健康检查结果。
 */
public record HealthCheckResult(Status status, String message) {

    public enum Status { HEALTHY, DEGRADED, UNHEALTHY }

    public static HealthCheckResult healthy() {
        return new HealthCheckResult(Status.HEALTHY, "OK");
    }

    public static HealthCheckResult degraded(String message) {
        return new HealthCheckResult(Status.DEGRADED, message);
    }

    public static HealthCheckResult unhealthy(String message) {
        return new HealthCheckResult(Status.UNHEALTHY, message);
    }
}
