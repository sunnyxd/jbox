package com.alibaba.jbox.thread;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jifang
 * @since 2017/1/18 下午4:12.
 */
public class LoggedDiscardPolicy extends ThreadPoolExecutor.DiscardPolicy implements LoggerInter {

    private String group;

    public LoggedDiscardPolicy(String group) {
        this.group = group;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {
        if (runnable instanceof AsyncRunnable) {
            String taskInfo = ((AsyncRunnable) runnable).getAsyncTaskInfo();

            String msg = String.format("policy: [Discard], task:[%s] execute reject, group:[%s] runnable queue remaining:[%s]",
                    taskInfo,
                    this.group,
                    executor.getQueue().remainingCapacity());

            LOGGER.warn(msg);
            MONITOR_LOGGER.warn(msg);
        }


        super.rejectedExecution(runnable, executor);
    }
}
