package com.alibaba.jbox.executor;

import com.alibaba.jbox.scheduler.AbstractScheduleTask;
import com.alibaba.jbox.stream.StreamForker;
import com.alibaba.jbox.utils.JboxUtils;
import com.alibaba.jbox.utils.ProxyUtil;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
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
public class ExecutorMonitor extends AbstractScheduleTask implements LoggerInter {

    private static final String ASYNC_KEY = "async";

    private static final String FUTURE_KEY = "future";

    private static final String CALLABLE_KEY = "callable";

    private static final ConcurrentMap<ExecutorService, ThreadPoolExecutor> executors = new ConcurrentHashMap<>();

    private long period;

    public ExecutorMonitor() {
        this(_1M_INTERVAL);
    }

    public ExecutorMonitor(long period) {
        this.period = period;
    }

    @Override
    public void invoke() throws Exception {

        StringBuilder logBuilder = new StringBuilder(1000);
        logBuilder.append("executors group as below:\n");

        ExecutorManager.executors.forEach((group, executorProxy) -> {
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
                            .collect(new Collector()))
                    .fork(FUTURE_KEY, stream -> stream
                            .filter(runnable -> runnable instanceof FutureTask)
                            .map(this::getFutureTaskInnerAsyncObject)
                            .collect(new Collector()))
                    .fork(CALLABLE_KEY, stream -> stream
                            .filter(callable -> callable instanceof AsyncCallable)
                            .collect(new Collector()));

            StreamForker.Results results = forker.getResults();
            StringBuilder asyncLogBuilder = results.get(ASYNC_KEY);
            StringBuilder futureLogBuilder = results.get(FUTURE_KEY);
            StringBuilder callableLogBuilder = results.get(CALLABLE_KEY);

            logBuilder
                    .append(asyncLogBuilder)
                    .append(futureLogBuilder)
                    .append(callableLogBuilder);
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

    private class Collector implements java.util.stream.Collector<Object, StringBuilder, StringBuilder> {

        @Override
        public Supplier<StringBuilder> supplier() {
            return StringBuilder::new;
        }

        @Override
        public BiConsumer<StringBuilder, Object> accumulator() {
            return (stringBuilder, object) -> {
                Method taskInfoMethod = ReflectionUtils.findMethod(object.getClass(), "taskInfo");
                ReflectionUtils.makeAccessible(taskInfoMethod);
                stringBuilder.append(" -> ")
                        .append("task: ")
                        .append(ReflectionUtils.invokeMethod(taskInfoMethod, object))
                        .append(", obj: ")
                        .append(Objects.hashCode(object))
                        .append("\n");
            };
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

    private Object getFutureTaskInnerAsyncObject(Object futureTask) {
        Object callable = JboxUtils.getFieldValue(futureTask, "callable");
        if (callable instanceof AsyncCallable) {
            return callable;
        } else {
            return JboxUtils.getFieldValue(callable, "task");
        }
    }

    @Override
    public long period() {
        return period;
    }
}
