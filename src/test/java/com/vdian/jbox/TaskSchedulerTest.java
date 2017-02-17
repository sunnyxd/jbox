package com.vdian.jbox;

import com.vdian.jbox.scheduler.ScheduleTask;
import com.vdian.jbox.scheduler.TaskScheduler;
import org.junit.Test;

/**
 * @author jifang
 * @since 16/10/20 上午11:46.
 */
public class TaskSchedulerTest {

    @Test
    public void test() throws Exception {
        TaskScheduler scheduler = new TaskScheduler();
        scheduler.register(new ScheduleTask() {

            @Override
            public void scheduleTask() {
                System.out.println("Helo");
            }

            @Override
            public long interval() {
                return 1000;
            }
        });
        scheduler.setUp();

        Thread.sleep(10000000);
        scheduler.tearDown();
    }
}
