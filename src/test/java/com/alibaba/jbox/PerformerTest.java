package com.alibaba.jbox;

import com.alibaba.jbox.utils.Performer;

/**
 * @author jifang
 * @since 2016/11/10 下午5:07.
 */
public class PerformerTest {

    public static void main(String[] args) {


        for (int i = 0; i < 1000000; ++i) {
        }

        Performer analyzer = new Performer();

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }

        double qps = analyzer.qps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.qps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.qps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.qps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.qps();
        System.out.println(qps);

        for (int i = 0; i < 10000; ++i) {
            analyzer.invoked();
        }
        qps = analyzer.qps();
        System.out.println(qps);
    }
}
