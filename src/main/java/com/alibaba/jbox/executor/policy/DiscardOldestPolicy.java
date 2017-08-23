package com.alibaba.jbox.executor.policy;

import com.alibaba.jbox.executor.AsyncRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jifang
 * @since 2017/1/18 下午4:31.
 */
public class DiscardOldestPolicy extends ThreadPoolExecutor.DiscardOldestPolicy {

    private static final Logger logger = LoggerFactory.getLogger("com.alibaba.jbox.executor");

    private static final Logger monitorLogger = LoggerFactory.getLogger("executor-monitor");

    private String group;

    public DiscardOldestPolicy(String group) {
        this.group = group;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (!executor.isShutdown()) {
            Runnable discardRunnable = executor.getQueue().poll();
            if (discardRunnable instanceof AsyncRunnable) {
                String taskInfo = ((AsyncRunnable) discardRunnable).taskInfo();

                String msg = String.format("policy: [DiscardOldest], task:[%s] discard, group:[%s] runnable queue remaining:[%s]",
                        taskInfo,
                        this.group,
                        executor.getQueue().remainingCapacity());

                logger.warn(msg);
                monitorLogger.warn(msg);
            }

            executor.execute(runnable);
        }
    }
}
