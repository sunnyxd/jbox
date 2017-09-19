package com.alibaba.jbox;

import java.util.concurrent.ArrayBlockingQueue;

import com.alibaba.jbox.executor.ExecutorLoggerInter;
import com.alibaba.jbox.executor.policy.CallerRunsPolicy;
import com.alibaba.jbox.utils.Performer;

import org.junit.Test;

/**
 * @author jifang
 * @since 2016/11/10 下午5:07.
 */
public class PerformerTest implements ExecutorLoggerInter {

    @Test
    public void test() {
        String s = generatePolicyLoggerContent("nihao", new CallerRunsPolicy("ss"), new ArrayBlockingQueue<Object>(30),
            "ss", 123);
        System.out.println(s);
    }

    public static void main(String[] args) {

        for (int i = 0; i < 1000000; ++i) {
        }

        Performer analyzer = new Performer("test");

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }

        double qps = analyzer.tps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.tps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.tps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.tps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        System.out.println(analyzer.rtString());

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        System.out.println(analyzer.tpsString());

    }
}
