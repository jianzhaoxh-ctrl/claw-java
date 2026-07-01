package com.openclaw.desktop.approval;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审批系统单元测试。
 */
class ApprovalSystemTest {

    private ApprovalManager manager;

    @BeforeEach
    void setup() {
        manager = new ApprovalManager(ApprovalPolicy.CONFIRM_DANGEROUS);
    }

    @Test
    void testAutoApprovePolicy() {
        manager.setPolicy(ApprovalPolicy.AUTO_APPROVE);
        assertFalse(manager.needsApproval("any_tool", "any_op"));
    }

    @Test
    void testConfirmAllPolicy() {
        manager.setPolicy(ApprovalPolicy.CONFIRM_ALL);
        assertTrue(manager.needsApproval("read_file", "read"));
    }

    @Test
    void testConfirmDangerousPolicy() {
        assertFalse(manager.needsApproval("read_file", "read"));
        assertTrue(manager.needsApproval("delete_file", "delete_file"));
        assertTrue(manager.needsApproval("shell_exec", "execute_elevated"));
    }

    @Test
    void testConfirmMutationsPolicy() {
        manager.setPolicy(ApprovalPolicy.CONFIRM_MUTATIONS);
        assertTrue(manager.needsApproval("write_file", "write_file"));
        assertTrue(manager.needsApproval("delete_file", "delete_file"));
        assertFalse(manager.needsApproval("read_file", "read"));
    }

    @Test
    void testAutoApprovedOperation() {
        var request = ApprovalRequest.of("read_file", "read", "Read a file", ApprovalRequest.ApprovalLevel.LOW);
        var result = manager.requestApproval(request).block();
        assertTrue(result.decision() == ApprovalResult.ApprovalDecision.APPROVED);
    }

    @Test
    void testDeniedWithoutCallback() {
        var request = ApprovalRequest.of("delete_file", "delete_file", "Delete a file", ApprovalRequest.ApprovalLevel.HIGH);
        var result = manager.requestApproval(request).block();
        assertTrue(result.decision() == ApprovalResult.ApprovalDecision.DENIED);
    }

    @Test
    void testApprovedWithCallback() {
        manager.setCallback(req -> Mono.just(ApprovalResult.approved(req.id())));
        var request = ApprovalRequest.of("delete_file", "delete_file", "Delete important file", ApprovalRequest.ApprovalLevel.HIGH);
        var result = manager.requestApproval(request).block();
        assertTrue(result.decision() == ApprovalResult.ApprovalDecision.APPROVED);
    }

    @Test
    void testDeniedWithCallback() {
        manager.setCallback(req -> Mono.just(ApprovalResult.denied(req.id(), "User rejected")));
        var request = ApprovalRequest.of("delete_file", "delete_file", "Delete a file", ApprovalRequest.ApprovalLevel.HIGH);
        var result = manager.requestApproval(request).block();
        assertTrue(result.decision() == ApprovalResult.ApprovalDecision.DENIED);
        assertEquals("User rejected", result.reason());
    }

    @Test
    void testAllowOnceCache() {
        manager.setPolicy(ApprovalPolicy.CONFIRM_ALL); // 所有操作都需要审批
        manager.setCallback(req -> Mono.just(ApprovalResult.approvedOnce(req.id())));
        var request1 = ApprovalRequest.of("write_file", "write_file", "Write config", ApprovalRequest.ApprovalLevel.NORMAL);
        var result1 = manager.requestApproval(request1).block();
        assertTrue(result1.decision() == ApprovalResult.ApprovalDecision.APPROVED);
        assertTrue(result1.allowOnce());

        // 第二次相同操作应自动批准（从缓存）
        manager.setCallback(null); // 无回调
        var request2 = ApprovalRequest.of("write_file", "write_file", "Write another config", ApprovalRequest.ApprovalLevel.NORMAL);
        var result2 = manager.requestApproval(request2).block();
        // 因为缓存了 allow-once，应自动批准
        assertTrue(result2.decision() == ApprovalResult.ApprovalDecision.APPROVED);
    }

    @Test
    void testClearAllowOnceCache() {
        manager.setCallback(req -> Mono.just(ApprovalResult.approvedOnce(req.id())));
        var request = ApprovalRequest.of("shell_exec", "execute_elevated", "Run elevated command", ApprovalRequest.ApprovalLevel.HIGH);
        manager.requestApproval(request).block();
        manager.clearAllowOnceCache();

        // 缓存已清除，再次需要审批
        manager.setCallback(null);
        var request2 = ApprovalRequest.of("shell_exec", "execute_elevated", "Run again", ApprovalRequest.ApprovalLevel.HIGH);
        var result2 = manager.requestApproval(request2).block();
        assertTrue(result2.decision() == ApprovalResult.ApprovalDecision.DENIED);
    }

    @Test
    void testApprovalHistory() {
        manager.setCallback(req -> Mono.just(ApprovalResult.approved(req.id())));
        var request = ApprovalRequest.of("delete_file", "delete_file", "Delete", ApprovalRequest.ApprovalLevel.HIGH);
        var result = manager.requestApproval(request).block();

        var history = manager.getHistory();
        assertTrue(history.containsKey(request.id()));
        assertEquals(result, history.get(request.id()));
    }

    @Test
    void testApprovalRequestFactory() {
        var request = ApprovalRequest.of("tool", "op", "desc", ApprovalRequest.ApprovalLevel.LOW);
        assertNotNull(request.id());
        assertEquals("tool", request.toolName());
        assertEquals("op", request.operation());
        assertEquals(ApprovalRequest.ApprovalLevel.LOW, request.level());
    }

    @Test
    void testApprovalResultFactory() {
        var approved = ApprovalResult.approved("req1");
        assertEquals(ApprovalResult.ApprovalDecision.APPROVED, approved.decision());
        assertFalse(approved.allowOnce());

        var once = ApprovalResult.approvedOnce("req2");
        assertTrue(once.allowOnce());

        var denied = ApprovalResult.denied("req3", "too dangerous");
        assertEquals(ApprovalResult.ApprovalDecision.DENIED, denied.decision());
        assertEquals("too dangerous", denied.reason());

        var deferred = ApprovalResult.deferred("req4");
        assertEquals(ApprovalResult.ApprovalDecision.DEFERRED, deferred.decision());
    }

    @Test
    void testSetPolicy() {
        manager.setPolicy(ApprovalPolicy.CONFIRM_ALL);
        assertEquals(ApprovalPolicy.CONFIRM_ALL, manager.getPolicy());
    }

    @Test
    void testDangerousOperationsList() {
        assertTrue(manager.needsApproval("shell_exec", "execute_elevated"));
        assertTrue(manager.needsApproval("send_email", "send_email"));
        assertTrue(manager.needsApproval("format_drive", "format_drive"));
    }
}
