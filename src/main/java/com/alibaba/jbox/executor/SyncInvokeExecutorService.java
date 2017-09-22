package com.alibaba.jbox.executor;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.common.base.Preconditions;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 14:04:00.
 */
class SyncInvokeExecutorService extends AbstractExecutorService {

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return Collections.emptyList();
    }

    @Override
    public boolean isShutdown() {
        return true;
    }

    @Override
    public boolean isTerminated() {
        return true;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return false;
    }

    @Override
    public void execute(Runnable command) {
        Preconditions.checkNotNull(command);
        command.run();
    }
}
