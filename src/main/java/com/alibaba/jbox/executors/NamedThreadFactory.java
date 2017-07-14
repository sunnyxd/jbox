package com.alibaba.jbox.executors;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author jifang
 * @since 2017/1/16 下午2:25.
 */
class NamedThreadFactory implements ThreadFactory {

    private final AtomicInteger number = new AtomicInteger(0);

    private String group;

    public NamedThreadFactory(String group) {
        this.group = group;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(String.format("%s-%s", group, number.getAndIncrement()));
        thread.setDaemon(true);
        return thread;
    }
}
