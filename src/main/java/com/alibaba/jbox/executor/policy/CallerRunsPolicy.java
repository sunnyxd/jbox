package com.alibaba.jbox.executor.policy;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorLoggerInner;

/**
 * @author jifang
 * @since 2017/1/16 下午2:18.
 */
public class CallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy implements ExecutorLoggerInner {

    private String group;

    public CallerRunsPolicy(String group) {
        this.group = group;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {

        if (runnable instanceof AsyncRunnable) {
            AsyncRunnable asyncRunnable = (AsyncRunnable)runnable;
            String message = generatePolicyLoggerContent(group, this, executor.getQueue(), asyncRunnable.taskInfo(),
                Objects.hashCode(asyncRunnable));

            logger.warn(message);
            monitor.warn(message);
        }

        super.rejectedExecution(runnable, executor);
    }
}
