// Author: dishanhai
package com.dishanhai.sh_better_ui.kernel.api.automation.rule;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** 由实际服务端集成实现。不得把客户端声称的在线、权限、库存或提交结果当成事实。 */
public interface RuleAdapter {
    boolean isOnline(UUID owner);
    boolean authorized(UUID owner, String targetKey);
    boolean dependencyAvailable(String targetKey);
    /** 同一库存网络的不同入口必须返回同一会话标识；默认适用于目标本身已规范化的集成。 */
    default String canonicalTarget(UUID owner, String targetKey) { return targetKey; }
    Inventory inventory(UUID owner, String targetKey, String item) throws Exception;
    CompletionStage<Submission> submit(UUID intentId, AutomationAction action) throws Exception;
    /** 只取消尚未真正提交的规划；已提交任务必须返回 false 并继续运行。 */
    default boolean cancelPending(UUID intentId) { return false; }
    TaskStatus query(UUID owner, String taskId) throws Exception;

    /** inFlight 包括适配器已经知道的全部在途量，不应重复叠加本引擎的任务。 */
    record Inventory(long stored, long inFlight) {
        public Inventory {
            if (stored < 0 || inFlight < 0) throw new IllegalArgumentException("Negative inventory");
        }
        public long available() {
            return Long.MAX_VALUE - stored < inFlight ? Long.MAX_VALUE : stored + inFlight;
        }
    }

    enum SubmissionStatus { ACCEPTED, DEFINITELY_NOT_EXECUTED, UNKNOWN }
    record Submission(SubmissionStatus status, String taskId) {
        public Submission {
            Objects.requireNonNull(status);
            if (status == SubmissionStatus.ACCEPTED && (taskId == null || taskId.isBlank())) {
                throw new IllegalArgumentException("Accepted task needs an id");
            }
        }
    }

    /** FAILED 必须表示原任务已经终止；无法确认时返回 UNKNOWN。 */
    enum TaskStatus { RUNNING, SUCCEEDED, FAILED, UNKNOWN }
}
