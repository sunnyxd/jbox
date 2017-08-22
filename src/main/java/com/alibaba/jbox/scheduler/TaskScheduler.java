package com.alibaba.jbox.scheduler;

import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jifang
 * @since 16/8/23 下午3:35.
 */
public class TaskScheduler {

    /**
     * 基本调度时间片(粒度不能小于100MS)
     */
    private static final int BASE_TIME_FRAGMENT = 100;

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable);
        thread.setName("jbox:task-scheduler");
        thread.setDaemon(true);

        return thread;
    });

    private static final ConcurrentMap<ScheduleTask, Pair<AtomicLong, Long>> taskMap = new ConcurrentHashMap<>();

    private boolean invokeOnStart;

    public TaskScheduler() {
        this(false);
    }

    public TaskScheduler(boolean invokeOnStart) {
        this.invokeOnStart = invokeOnStart;
    }

    public void register(@NonNull ScheduleTask task) {
        taskMap.put(task, Pair.of(new AtomicLong(0L), task.period() / BASE_TIME_FRAGMENT));
        ScheduleTask.SCHEDULE_TASK_LOGGER.info("task [{}] registered, period [{}]", task.taskName(), task.period());
    }

    @PostConstruct
    public void start() {
        executor.scheduleAtFixedRate(this::triggerTask,
                invokeOnStart ? 0 : BASE_TIME_FRAGMENT,
                BASE_TIME_FRAGMENT, TimeUnit.MILLISECONDS);

        ScheduleTask.SCHEDULE_TASK_LOGGER.info("TaskScheduler Start ...");
    }

    private void triggerTask() {

        taskMap.forEach((task, pair) -> {
            AtomicLong currentInterval = pair.getLeft();
            long currentIntervalValue = currentInterval.addAndGet(1L);
            long periodThresholdValue = pair.getRight();

            if (currentIntervalValue >= periodThresholdValue) {
                // 执行任务
                invokeTask(task);
                // 重新开始计时
                currentInterval.set(0L);
            }
        });
    }

    private void invokeTask(ScheduleTask task) {
        try {
            task.invoke();
            ScheduleTask.SCHEDULE_TASK_LOGGER.debug("task [{}] invoked", task.taskName());
        } catch (Exception e) {
            ScheduleTask.SCHEDULE_TASK_LOGGER.error("task [{}] invoke error", task.taskName(), e);
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();

        ScheduleTask.SCHEDULE_TASK_LOGGER.info("TaskScheduler shutdown ...");
    }
}
