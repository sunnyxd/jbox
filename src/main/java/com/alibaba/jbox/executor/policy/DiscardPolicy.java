package com.alibaba.jbox.executor.policy;

import java.util.Objects;
import java.util.concurrent.ThreadPoolExecutor;

import com.alibaba.jbox.executor.AsyncRunnable;
import com.alibaba.jbox.executor.ExecutorLoggerInner;

/**
 * @author jifang
 * @since 2017/1/18 下午4:12.
 */
public class DiscardPolicy extends ThreadPoolExecutor.DiscardPolicy implements ExecutorLoggerInner {

    private String group;

    public DiscardPolicy(String group) {
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

        // 直接将新元素扔掉
        super.rejectedExecution(runnable, executor);
    }
}
