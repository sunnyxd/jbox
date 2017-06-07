package com.alibaba.jbox.thread;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jifang
 * @since 2017/1/16 下午2:18.
 */
public class LoggedCallerRunsPolicy extends ThreadPoolExecutor.CallerRunsPolicy implements LoggerInter {

    private String group;

    public LoggedCallerRunsPolicy(String group) {
        this.group = group;
    }

    @Override
    public void rejectedExecution(Runnable runnable, ThreadPoolExecutor executor) {

        if (runnable instanceof AsyncRunnable) {
            String taskInfo = ((AsyncRunnable) runnable).getAsyncTaskInfo();

            String msg = String.format("policy: [CallerRuns], task:[%s] execute reject, group:[%s] runnable queue remaining:[%s]",
                    taskInfo,
                    this.group,
                    executor.getQueue().remainingCapacity());

            LOGGER.warn(msg);
            MONITOR_LOGGER.warn(msg);
        }

        super.rejectedExecution(runnable, executor);
    }
}
