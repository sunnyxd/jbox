package com.alibaba.jbox.executors;

import com.alibaba.jbox.scheduler.AbstractScheduleTask;
import com.alibaba.jbox.stream.StreamForker;
import com.alibaba.jbox.utils.DateUtils;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author jifang
 * @since 2017/1/16 下午5:36.
 */
public class ThreadPoolMonitor extends AbstractScheduleTask implements LoggerInter {

    private static final ConcurrentMap<ExecutorService, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();

    @Override
    public void invoke() throws Exception {

        StringBuilder log = new StringBuilder(1000);
        log.append("*** Thread Pool Monitor - ").append(DateUtils.format(new Date())).append(" ***\n");

        ThreadPoolManager.executors.forEach((group, executorProxy) -> {
            ThreadPoolExecutor executor = getThreadPoolExecutor(executorProxy);

            BlockingQueue<Runnable> runnableQueue = executor.getQueue();
            log.append(String.format("group:[%s], threads:[%s], active:[%d], task in queue:[%d]\n",
                    group,
                    executor.getPoolSize(),
                    executor.getActiveCount(),
                    runnableQueue.size()));

            StreamForker<Runnable> forker = new StreamForker<>(runnableQueue.stream())
                    .fork("async", stream -> stream
                            .filter(runnable -> runnable instanceof AsyncRunnable)
                            .map(runnable -> (AsyncRunnable) runnable)
                            .collect(new Collector()))
                    .fork("future", stream -> stream
                            .filter(runnable -> runnable instanceof FutureTask)
                            .map(this::getFutureTaskInnerAsyncRunnable)
                            .collect(new Collector()));

            StreamForker.Results results = forker.getResults();
            StringBuilder asyncLogBuilder = results.get("async");
            StringBuilder futureLogBuilder = results.get("future");
            log.append(asyncLogBuilder).append(futureLogBuilder);
        });
        MONITOR_LOGGER.info(log.toString());
    }

    private class Collector implements java.util.stream.Collector<AsyncRunnable, StringBuilder, StringBuilder> {

        @Override
        public Supplier<StringBuilder> supplier() {
            return StringBuilder::new;
        }

        @Override
        public BiConsumer<StringBuilder, AsyncRunnable> accumulator() {
            return (stringBuilder, asyncRunnable) -> stringBuilder.append(" -> ")
                    .append(asyncRunnable.taskInfo())
                    .append(", obj: ")
                    .append(asyncRunnable.hashCode())
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
        try {
            Field callable = FutureTask.class.getDeclaredField("callable");
            callable.setAccessible(true);
            Object runnableAdapter = callable.get(runnable);

            Field task = runnableAdapter.getClass().getDeclaredField("task");
            task.setAccessible(true);
            return (AsyncRunnable) task.get(runnableAdapter);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExecutorException(e);
        }
    }

    private ThreadPoolExecutor getThreadPoolExecutor(ExecutorService executorProxy) {
        return executors.computeIfAbsent(executorProxy, (proxy) -> {
            ThreadPoolExecutor executor;

            if (proxy instanceof ThreadPoolExecutor) {
                executor = (ThreadPoolExecutor) proxy;
            }
            // proxy by CGLIB
            else {
                Object target = getCglibTarget(proxy);
                if (target instanceof ThreadPoolExecutor) {
                    executor = (ThreadPoolExecutor) target;
                } else {
                    executor = getFinalizableDelegatedExecutorServiceInnerExecutor(target);
                }
            }

            return executor;
        });
    }

    private ThreadPoolExecutor getFinalizableDelegatedExecutorServiceInnerExecutor(Object target) {
        try {
            Field executorField = target.getClass().getSuperclass().getDeclaredField("e");
            executorField.setAccessible(true);
            return (ThreadPoolExecutor) executorField.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExecutorException(e);
            // IllegalAccessException no case
        }
    }

    private Object getCglibTarget(Object proxy) {
        try {
            Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
            h.setAccessible(true);
            Object dynamicAdvisedInterceptor = h.get(proxy);
            Field target = dynamicAdvisedInterceptor.getClass().getDeclaredField("target");
            target.setAccessible(true);
            return target.get(dynamicAdvisedInterceptor);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new ExecutorException(e);
            // IllegalAccessException no case
        }
    }

    @Override
    public long period() {
        return _1M_INTERVAL;
    }
}
