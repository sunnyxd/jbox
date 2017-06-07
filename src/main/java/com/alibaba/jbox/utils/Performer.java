package com.alibaba.jbox.utils;


import com.alibaba.jbox.annotation.ThreadSafe;

import java.util.Calendar;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 性能监控器: 监控qps & rt
 * ${code ThreadSafe}
 *
 * @author jifang
 * @since 16/11/9 下午2:53.
 */
@ThreadSafe
public class Performer {

    private AtomicLong totalInvokeCount = new AtomicLong(0);

    private volatile long befCount;

    private volatile long befMillis;

    private volatile long todayEndMillis;

    public Performer() {
        init();
    }

    public long invoked() {
        return totalInvokeCount.incrementAndGet();
    }

    public long invoked(long size) {
        return totalInvokeCount.addAndGet(size);
    }

    public long totalInvoked() {
        return totalInvokeCount.get();
    }

    public double qps() {
        long curMillis = System.currentTimeMillis();
        long curCount = totalInvokeCount.get();

        long invokedCount = curCount - befCount;
        long usedMillis = curMillis - befMillis;

        double qps;
        if (usedMillis == 0) {
            qps = invokedCount * 1000.0;
        } else {
            qps = invokedCount * 1000.0 / usedMillis;
            befMillis = curMillis;
        }
        befCount = curCount;

        // reset everyday
        if (curMillis > todayEndMillis) {
            init();
        }

        return qps;
    }

    public double rt() {
        long curMillis = System.currentTimeMillis();
        long curCount = totalInvokeCount.get();

        long usedMillis = curMillis - befMillis;

        double rt;
        long invokedCount = curCount - befCount;
        if (invokedCount == 0) {
            rt = 0.0;
        } else {
            rt = usedMillis * 1.0 / invokedCount;
            befMillis = curMillis;
        }
        befCount = curCount;

        if (curMillis > todayEndMillis) {
            init();
        }

        return rt;
    }

    private void init() {
        befMillis = System.currentTimeMillis();
        todayEndMillis = endMillisToday();
        befCount = 0;
        totalInvokeCount.set(0);
    }

    private long endMillisToday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static void main(String[] args) {
        System.out.println();
    }
}
