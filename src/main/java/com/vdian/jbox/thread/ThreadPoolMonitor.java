package com.vdian.jbox.thread;

import com.vdian.jbox.scheduler.AbstractScheduleTask;
import com.vdian.jbox.utils.DateUtils;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author jifang
 * @since 2017/1/16 下午5:36.
 */
public class ThreadPoolMonitor extends AbstractScheduleTask implements LoggerInter {

    @Override
    public void scheduleTask() throws Exception {

        StringBuilder sb = new StringBuilder(1000);
        sb.append("---------------------------Thread Pool Monitor---")
                .append(DateUtils.format(new Date()))
                .append("-------------\n");

        Map<String, ExecutorService> executors = ThreadPoolManager.EXECUTOR_MAP;
        for (Map.Entry<String, ExecutorService> entry : executors.entrySet()) {
            ThreadPoolExecutor executor = getExecutor(entry.getValue());
            BlockingQueue<Runnable> taskQueue = executor.getQueue();
            sb.append(String.format("group: %10s, [%d] threads in pool, active:[%d], [%d] task in queue\n",
                    entry.getKey(),
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    taskQueue.size()));

            for (Runnable runnable : taskQueue) {
                if (runnable instanceof AsyncRunnable) {
                    sb.append("\t")
                            .append(((AsyncRunnable) runnable).getAsyncTaskInfo())
                            .append("\n");
                }
            }
        }

        MONITOR_LOGGER.info(sb.toString());
    }

    @Override
    public long interval() {
        return _1M_INTERVAL;
    }

    private ThreadPoolExecutor getExecutor(ExecutorService value) throws Exception {
        ThreadPoolExecutor executor;

        // proxy by CGLIB
        if (!(value instanceof ThreadPoolExecutor)) {
            Object target = getCglibTarget(value);
            if (target instanceof ThreadPoolExecutor) {
                executor = (ThreadPoolExecutor) target;
            } else {
                //Delegated by FinalizableDelegatedExecutorService
                Field executorField = target.getClass().getSuperclass().getDeclaredField("e");
                executorField.setAccessible(true);
                executor = (ThreadPoolExecutor) executorField.get(target);
            }
        } else {
            executor = (ThreadPoolExecutor) value;
        }

        return executor;
    }

    private Object getCglibTarget(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        h.setAccessible(true);
        Object dynamicAdvisedInterceptor = h.get(proxy);

        Field target = dynamicAdvisedInterceptor.getClass().getDeclaredField("target");
        target.setAccessible(true);

        return target.get(dynamicAdvisedInterceptor);
    }
}
