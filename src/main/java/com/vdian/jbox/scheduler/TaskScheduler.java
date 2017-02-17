package com.vdian.jbox.scheduler;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jifang
 * @since 16/8/23 下午3:35.
 */
public class TaskScheduler {

    // 调度基本时间片(粒度不能小于100MS)
    private static final int BASE_TIME_FRAGMENT = 100;

    private static final Timer executor = new Timer("TaskScheduler");

    private static final Map<ScheduleTask, Long> taskMap = new ConcurrentHashMap<>();

    private boolean invokeOnStart;

    public TaskScheduler() {
        this(false);
    }

    public TaskScheduler(boolean invokeOnStart) {
        this.invokeOnStart = invokeOnStart;
    }

    private void start() {
        executor.schedule(new TimerTask() {
            @Override
            public void run() {
                for (ScheduleTask task : taskMap.keySet()) {

                    long currentInterval = taskMap.get(task) + 1;
                    long needInterval = task.interval() / BASE_TIME_FRAGMENT;

                    if (currentInterval >= needInterval) {
                        // 执行任务
                        invokeTask(task);
                        // 重新计时
                        currentInterval = 0;
                    }
                    taskMap.put(task, currentInterval);
                }
            }
        }, 0, BASE_TIME_FRAGMENT);
    }

    public void register(ScheduleTask task) {
        taskMap.put(task, 0L);

        if (invokeOnStart) {
            invokeTask(task);
        }

        String name = task.getClass().getName();
        long interval = task.interval();
        ScheduleTask.SCHEDULE_TASK_LOGGER.info("task [{}] registered, interval [{}]", name, interval);
    }

    private void invokeTask(ScheduleTask task) {
        try {
            task.scheduleTask();
            ScheduleTask.SCHEDULE_TASK_LOGGER.debug("task {} invoked", task);
        } catch (Exception e) {
            ScheduleTask.SCHEDULE_TASK_LOGGER.error("task {} invoke error", task, e);
        }
    }

    @PostConstruct
    public void setUp() {
        this.start();

        ScheduleTask.SCHEDULE_TASK_LOGGER.info("TaskScheduler Start ...");
    }

    @PreDestroy
    public void tearDown() {
        executor.cancel();

        ScheduleTask.SCHEDULE_TASK_LOGGER.info("TaskScheduler Stop ... {}");
    }
}
