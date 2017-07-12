package com.alibaba.jbox;

import com.alibaba.jbox.scheduler.ScheduleTask;
import com.alibaba.jbox.scheduler.TaskScheduler;
import org.junit.Test;

/**
 * @author jifang
 * @since 16/10/20 上午11:46.
 */
public class TaskSchedulerTest {

    @Test
    public void test() throws Exception {
        TaskScheduler scheduler = new TaskScheduler(true);
        scheduler.register(new ScheduleTask() {

            @Override
            public void invoke() {
                System.out.println("Helo");
            }

            @Override
            public long period() {
                return 1000;
            }
        });
        scheduler.start();

        Thread.sleep(10000000);
        scheduler.shutdown();
    }
}
