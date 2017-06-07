package com.alibaba.jbox;

import com.alibaba.jbox.flood.AbstractZKFlood;

import java.util.Random;

/**
 * @author jifang
 * @since 2016/11/3 下午4:21.
 */
public class FeedCenterFloodExp extends AbstractZKFlood {

    public FeedCenterFloodExp(String connectString) throws Exception {
        super(connectString);
    }

    public static void main(String[] args) throws Exception {
        FeedCenterFloodExp floodExp = new FeedCenterFloodExp("127.0.0.1:2181,127.0.0.1:2182,127.0.0.1:2183");
        Random random = new Random();

        long totalExpCount = 1_000_000_000L;
        long inCount = 0;
        for (long i = 0; i < totalExpCount; ++i) {
            boolean in = floodExp.doExperiment(random.nextDouble());
            Thread.sleep(1);
            inCount += (in ? 1 : 0);
        }

        System.out.println("in value count " + inCount + ", rate " + (100.0 * inCount / totalExpCount) + "%");
    }

    @Override
    protected String nodeName() {
        return "/feedcenter";
    }
}
