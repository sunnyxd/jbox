package com.alibaba.jbox.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang
 * @since 16/10/20 上午8:15.
 */
public interface ScheduleTask {

    Logger SCHEDULE_TASK_LOGGER = LoggerFactory.getLogger("task-scheduler");

    long _1S_INTERVAL = 1000L;

    long _10S_INTERVAL = 10 * _1S_INTERVAL;

    long _1M_INTERVAL = 6 * _10S_INTERVAL;

    long HALF_AN_HOUR_INTERVAL = 30 * _1M_INTERVAL;

    long ONE_HOUR_INTERVAL = 2 * HALF_AN_HOUR_INTERVAL;

    long _12_HOUR_INTERVAL = 12 * ONE_HOUR_INTERVAL;

    long ONE_DAY_INTERVAL = 2 * _12_HOUR_INTERVAL;

    void invoke() throws Exception;

    long period();

    default String taskName() {
        return this.getClass().getName();
    }
}
