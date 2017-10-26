package com.alibaba.jbox.scheduler;

import com.alibaba.fastjson.JSONObject;

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

        Thread.sleep(100000);
        scheduler.shutdown();
    }

    @Test
    public void test2() {
        JSONObject jsonObject = JSONObject.parseObject("{\n"
            + "\"nihao\":\"nihao\",\n"
            + "\"nihao\":\"test\"\n"
            + "}");
        System.out.println(jsonObject);
    }
}
