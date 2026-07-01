package com.openclaw.desktop.channel.matrix;

/**
 * Matrix 配置。
 */
public record MatrixConfig(
    String homeserver,   // 如 https://matrix.org
    String username,
    String password,
    String defaultRoomId, // 如 !abc123:matrix.org
    boolean enabled
) {
    public static MatrixConfig defaults() {
        return new MatrixConfig("https://matrix.org", "", "", "", false);
    }
}
