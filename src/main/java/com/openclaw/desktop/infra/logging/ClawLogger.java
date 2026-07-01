package com.openclaw.desktop.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一日志工具 — 封装常用日志模式。
 */
public final class ClawLogger {

    private final Logger logger;

    private ClawLogger(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
    }

    public static ClawLogger forClass(Class<?> clazz) {
        return new ClawLogger(clazz);
    }

    public void info(String msg, Object... args) { logger.info(msg, args); }
    public void debug(String msg, Object... args) { logger.debug(msg, args); }
    public void warn(String msg, Object... args) { logger.warn(msg, args); }
    public void error(String msg, Object... args) { logger.error(msg, args); }
    public void error(String msg, Throwable t) { logger.error(msg, t); }
}
