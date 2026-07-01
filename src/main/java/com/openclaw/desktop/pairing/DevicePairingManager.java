package com.openclaw.desktop.pairing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 设备配对管理器 — 手机/平板与桌面端配对。
 * 对应 OpenClaw 的 pairing 模块。
 *
 * 配对流程：
 * 1. 桌面端生成配对码（6位数字）
 * 2. 手机端扫描二维码或手动输入配对码
 * 3. 手机端发送配对请求（包含设备信息 + 配对码）
 * 4. 桌面端验证配对码，生成设备 Token
 * 5. 手机端使用 Token 建立持久连接
 *
 * 安全：
 * - 配对码 5 分钟过期
 * - 设备 Token 使用 SecureRandom 生成
 * - 支持撤销设备访问权限
 */
public class DevicePairingManager {

    private static final Logger log = LoggerFactory.getLogger(DevicePairingManager.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long PAIRING_CODE_TTL_MS = 5 * 60 * 1000; // 5 分钟

    private final Map<String, PairingSession> pendingPairings = new ConcurrentHashMap<>();
    private final Map<String, PairedDevice> pairedDevices = new ConcurrentHashMap<>();

    /**
     * 生成配对码。
     */
    public PairingSession generatePairingCode() {
        var code = String.format("%06d", RANDOM.nextInt(1_000_000));
        var session = new PairingSession(code, Instant.now(), Instant.now().plusMillis(PAIRING_CODE_TTL_MS));
        pendingPairings.put(code, session);
        log.info("Pairing code generated: {} (expires in 5 min)", code);
        return session;
    }

    /**
     * 验证配对码（手机端调用）。
     */
    public PairingResult verifyPairing(String code, DeviceInfo deviceInfo) {
        var session = pendingPairings.get(code);
        if (session == null) {
            return PairingResult.failed("配对码无效");
        }
        if (Instant.now().isAfter(session.expiresAt())) {
            pendingPairings.remove(code);
            return PairingResult.failed("配对码已过期，请重新生成");
        }

        // 生成设备 Token
        var deviceToken = generateToken();
        var deviceId = java.util.UUID.randomUUID().toString();
        var device = new PairedDevice(
            deviceId,
            deviceInfo.name(),
            deviceInfo.platform(),
            deviceInfo.osVersion(),
            deviceToken,
            Instant.now(),
            Instant.now(),
            true
        );
        pairedDevices.put(deviceId, device);
        pendingPairings.remove(code);

        log.info("Device paired: {} ({}) → deviceId={}", deviceInfo.name(), deviceInfo.platform(), deviceId);
        return PairingResult.success(deviceId, deviceToken);
    }

    /**
     * 撤销设备。
     */
    public boolean revokeDevice(String deviceId) {
        var removed = pairedDevices.remove(deviceId);
        if (removed != null) {
            log.info("Device revoked: {} ({})", deviceId, removed.name());
            return true;
        }
        return false;
    }

    /**
     * 验证设备 Token（用于 API 鉴权）。
     */
    public Optional<PairedDevice> verifyToken(String deviceToken) {
        return pairedDevices.values().stream()
            .filter(d -> d.deviceToken().equals(deviceToken) && d.active())
            .findFirst();
    }

    /**
     * 列出所有已配对设备。
     */
    public Collection<PairedDevice> listDevices() {
        return pairedDevices.values();
    }

    /**
     * 清理过期配对码。
     */
    public void cleanupExpiredCodes() {
        var now = Instant.now();
        pendingPairings.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }

    // ---- internal ----

    private String generateToken() {
        var bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // ---- records ----

    public record PairingSession(String code, Instant createdAt, Instant expiresAt) {}

    public record DeviceInfo(String name, String platform, String osVersion, String model) {}

    public record PairedDevice(
        String deviceId, String name, String platform, String osVersion,
        String deviceToken, Instant pairedAt, Instant lastSeenAt, boolean active
    ) {}

    public record PairingResult(boolean success, String deviceId, String deviceToken, String errorMessage) {
        static PairingResult success(String deviceId, String token) {
            return new PairingResult(true, deviceId, token, null);
        }
        static PairingResult failed(String error) {
            return new PairingResult(false, null, null, error);
        }
    }
}
