package com.yr.perftest.platform.auxscript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 辅助脚本生命周期钩子（模块 09）：执行启动后跑前置脚本，执行终结后跑后置脚本。
 * 钩子在事务 afterCommit 中触发，此时外层事务资源仍绑定在线程上，因此必须用
 * REQUIRES_NEW 挂起外层事务再执行，否则会误入已提交的死亡事务。辅助脚本失败
 * 绝不阻断主流程，只记录执行结果。
 */
@Component
public class AuxScriptLifecycle {
    private static final Logger log = LoggerFactory.getLogger(AuxScriptLifecycle.class);

    private final AuxScriptExecutor executor;
    private final TransactionTemplate newTransactionTemplate;

    public AuxScriptLifecycle(AuxScriptExecutor executor, PlatformTransactionManager transactionManager) {
        this.executor = executor;
        this.newTransactionTemplate = new TransactionTemplate(transactionManager);
        this.newTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void afterExecutionStarted(long executionId) {
        try {
            newTransactionTemplate.executeWithoutResult(status ->
                    executor.runPhase(executionId, AuxScriptPhase.PRE));
        } catch (RuntimeException exception) {
            log.warn("aux pre scripts failed for execution {}: {}", executionId, exception.getMessage(), exception);
        }
    }

    public void afterExecutionFinished(long executionId) {
        try {
            newTransactionTemplate.executeWithoutResult(status ->
                    executor.runPhase(executionId, AuxScriptPhase.POST));
        } catch (RuntimeException exception) {
            log.warn("aux post scripts failed for execution {}: {}", executionId, exception.getMessage(), exception);
        }
    }
}
