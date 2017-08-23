package com.alibaba.jbox.executor.policy;

import com.alibaba.jbox.executor.AsyncRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jifang
 * @since 2017/1/18 下午4:12.
 */
public class DiscardPolicy extends ThreadPoolExecutor.DiscardPolicy {

    private static final Logger logger = LoggerFactory.getLogger("com.alibaba.jbox.executor");

    private static final Logger monitorLogger = LoggerFactory.getLogger("executor-monitor");

    private String group;

    public DiscardPolicy(String group) {
        this.group = group;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (runnable instanceof AsyncRunnable) {
            String taskInfo = ((AsyncRunnable) runnable).taskInfo();

            String msg = String.format("policy: [DiscardPolicy], task:[%s] execute reject, group:[%s] runnable queue remaining:[%s]",
                    taskInfo,
                    this.group,
                    executor.getQueue().remainingCapacity());

            logger.warn(msg);
            monitorLogger.warn(msg);
        }


        super.rejectedExecution(runnable, executor);
    }
}
