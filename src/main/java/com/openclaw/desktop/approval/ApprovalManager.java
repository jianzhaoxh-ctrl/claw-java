package com.openclaw.desktop.approval;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批管理器 — 管理操作审批流程。
 * 对应 OpenClaw 的 tools.exec.ask / host approvals 机制。
 *
 * <p>核心功能：
 * <ul>
 *   <li>根据审批策略判断操作是否需要确认</li>
 *   <li>自动批准或提交审批请求</li>
 *   <li>一次性批准（allow-once）缓存</li>
 *   <li>审批历史记录</li>
 * </ul>
 */
public class ApprovalManager {
    private static final Logger log = LoggerFactory.getLogger(ApprovalManager.class);

    /** 危险操作列表 — 这些操作总是需要确认 */
    private static final Set<String> DANGEROUS_OPERATIONS = Set.of(
        "delete_file", "send_email", "execute_elevated",
        "reset_session", "remove_provider", "shell_exec",
        "format_drive", "modify_system"
    );

    /** 修改操作列表 — CONFIRM_MUTATIONS 模式下需要确认 */
    private static final Set<String> MUTATION_OPERATIONS = Set.of(
        "write_file", "edit_file", "move_file",
        "create_file", "config_set", "session_reset"
    );

    private ApprovalPolicy policy;
    private final Map<String, ApprovalResult> history = new ConcurrentHashMap<>();
    private final Map<String, Boolean> allowOnceCache = new ConcurrentHashMap<>();

    /** 审批回调 — UI 层实现此接口以弹出确认对话框 */
    private ApprovalCallback callback;

    public ApprovalManager() {
        this(ApprovalPolicy.CONFIRM_DANGEROUS);
    }

    public ApprovalManager(ApprovalPolicy policy) {
        this.policy = policy;
    }

    /**
     * 设置审批回调。
     */
    public void setCallback(ApprovalCallback callback) {
        this.callback = callback;
    }

    /**
     * 判断操作是否需要审批。
     */
    public boolean needsApproval(String toolName, String operation) {
        return switch (policy) {
            case AUTO_APPROVE    -> false;
            case CONFIRM_ALL     -> true;
            case CONFIRM_DANGEROUS -> DANGEROUS_OPERATIONS.contains(operation) || DANGEROUS_OPERATIONS.contains(toolName);
            case CONFIRM_MUTATIONS -> DANGEROUS_OPERATIONS.contains(operation) ||
                                      DANGEROUS_OPERATIONS.contains(toolName) ||
                                      MUTATION_OPERATIONS.contains(operation) ||
                                      MUTATION_OPERATIONS.contains(toolName);
        };
    }

    /**
     * 请求审批 — 如果不需要审批则自动批准，否则提交给回调。
     */
    public Mono<ApprovalResult> requestApproval(ApprovalRequest request) {
        // 检查一次性批准缓存
        var cacheKey = request.toolName() + ":" + request.operation();
        if (allowOnceCache.containsKey(cacheKey)) {
            log.info("Operation approved from cache: {} {}", request.toolName(), request.operation());
            return Mono.just(ApprovalResult.approved(request.id()));
        }

        if (!needsApproval(request.toolName(), request.operation())) {
            log.debug("Operation auto-approved: {} {}", request.toolName(), request.operation());
            var result = ApprovalResult.approved(request.id());
            history.put(request.id(), result);
            return Mono.just(result);
        }

        // 需要审批
        log.info("Operation requires approval: {} {} (level={})",
            request.toolName(), request.operation(), request.level());

        if (callback != null) {
            return callback.requestApproval(request)
                .doOnNext(result -> {
                    history.put(request.id(), result);
                    if (result.allowOnce()) {
                        allowOnceCache.put(cacheKey, true);
                        log.info("Allow-once granted for: {} {}", request.toolName(), request.operation());
                    }
                });
        }

        // 无回调 — 默认拒绝危险操作
        log.warn("No approval callback — denying operation: {} {}", request.toolName(), request.operation());
        return Mono.just(ApprovalResult.denied(request.id(), "No approval callback configured"));
    }

    /**
     * 设置审批策略。
     */
    public void setPolicy(ApprovalPolicy policy) {
        this.policy = policy;
        log.info("Approval policy changed to: {}", policy);
    }

    /**
     * 获取当前策略。
     */
    public ApprovalPolicy getPolicy() {
        return policy;
    }

    /**
     * 清除一次性批准缓存。
     */
    public void clearAllowOnceCache() {
        allowOnceCache.clear();
        log.info("Allow-once cache cleared");
    }

    /**
     * 获取审批历史。
     */
    public Map<String, ApprovalResult> getHistory() {
        return Map.copyOf(history);
    }

    /**
     * 审批回调接口 — UI 层实现此接口。
     */
    public interface ApprovalCallback {
        Mono<ApprovalResult> requestApproval(ApprovalRequest request);
    }
}
