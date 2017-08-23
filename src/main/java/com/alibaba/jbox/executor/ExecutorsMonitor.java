package com.alibaba.jbox.executor;

import com.alibaba.jbox.scheduler.AbstractScheduleTask;
import com.alibaba.jbox.stream.StreamForker;
import com.alibaba.jbox.utils.JboxUtils;
import com.alibaba.jbox.utils.ProxyUtil;

import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.2
 * @since 2017/8/22 15:32:00.
 */
public class ExecutorsMonitor extends AbstractScheduleTask implements LoggerInter {

    private static final String ASYNC_KEY = "async";

    private static final String FUTURE_KEY = "future";

    private static final ConcurrentMap<ExecutorService, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();

    private long period;

    public ExecutorsMonitor() {
        this(_1M_INTERVAL);
    }

    public ExecutorsMonitor(long period) {
        this.period = period;
    }

    @Override
    public void invoke() throws Exception {

        StringBuilder logBuilder = new StringBuilder(1000);
        logBuilder.append("executor groups as below:\n");

        ExecutorsManager.executors.forEach((group, executorProxy) -> {
            ThreadPoolExecutor executor = getThreadPoolExecutor(executorProxy);

            BlockingQueue<Runnable> queue = executor.getQueue();
            logBuilder.append(String.format("group:[%s], threads:[%s], active:[%d], task in queue:[%d], remain:[%d]\n",
                    group,
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    queue.size(),
                    queue.remainingCapacity()));

            StreamForker<Runnable> forker = new StreamForker<>(queue.stream())
                    .fork(ASYNC_KEY, stream -> stream
                            .filter(runnable -> runnable instanceof AsyncRunnable)
                            .map(runnable -> (AsyncRunnable) runnable)
                            .collect(new Collector()))
                    .fork(FUTURE_KEY, stream -> stream
                            .filter(runnable -> runnable instanceof FutureTask)
                            .map(this::getFutureTaskInnerAsyncRunnable)
                            .collect(new Collector()));

            StreamForker.Results results = forker.getResults();
            StringBuilder asyncLogBuilder = results.get(ASYNC_KEY);
            StringBuilder futureLogBuilder = results.get(FUTURE_KEY);

            logBuilder
                    .append(asyncLogBuilder)
                    .append(futureLogBuilder);
        });

        monitorLogger.info(logBuilder.toString());
    }

    private ThreadPoolExecutor getThreadPoolExecutor(ExecutorService executorProxy) {
        return executors.computeIfAbsent(executorProxy, (proxy) -> {

            ThreadPoolExecutor executor = null;

            if (proxy instanceof ThreadPoolExecutor) {
                executor = (ThreadPoolExecutor) proxy;
            } else if (Proxy.isProxyClass(proxy.getClass())) {
                Object target = ProxyUtil.getProxyTarget(proxy);
                if (target instanceof ThreadPoolExecutor) {
                    executor = (ThreadPoolExecutor) target;
                } else {
                    executor = (ThreadPoolExecutor) JboxUtils.getFieldValue(target, "e");
                }
            }

            return executor;
        });
    }

    private class Collector implements java.util.stream.Collector<AsyncRunnable, StringBuilder, StringBuilder> {

        @Override
        public Supplier<StringBuilder> supplier() {
            return StringBuilder::new;
        }

        @Override
        public BiConsumer<StringBuilder, AsyncRunnable> accumulator() {
            return (stringBuilder, asyncRunnable) -> stringBuilder.append(" -> ")
                    .append("task: ")
                    .append(asyncRunnable.taskInfo())
                    .append(", obj: ")
                    .append(Objects.hashCode(asyncRunnable))
                    .append("\n");
        }

        @Override
        public BinaryOperator<StringBuilder> combiner() {
            return StringBuilder::append;
        }

        @Override
        public Function<StringBuilder, StringBuilder> finisher() {
            return Function.identity();
        }

        @Override
        public Set<Characteristics> characteristics() {
            return EnumSet.of(Characteristics.CONCURRENT, Characteristics.UNORDERED, Characteristics.IDENTITY_FINISH);
        }
    }

    private AsyncRunnable getFutureTaskInnerAsyncRunnable(Runnable runnable) {
        return (AsyncRunnable) JboxUtils.getFieldValue(runnable, "callable", "task");
    }

    @Override
    public long period() {
        return period;
    }
}
