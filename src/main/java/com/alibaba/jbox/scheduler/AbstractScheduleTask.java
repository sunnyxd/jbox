package com.alibaba.jbox.scheduler;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

/**
 * @author jifang
 * @since 16/10/20 上午8:28.
 */
public abstract class AbstractScheduleTask implements ScheduleTask {

    @Resource
    private TaskScheduler taskScheduler;

    @PostConstruct
    public void setUp() {
        taskScheduler.register(this);
    }
}
