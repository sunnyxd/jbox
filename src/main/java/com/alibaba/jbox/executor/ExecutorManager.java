package com.alibaba.jbox.executor;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PreDestroy;

import com.alibaba.jbox.executor.policy.CallerRunsPolicy;
import com.alibaba.jbox.scheduler.ScheduleTask;

/**
 * 线程池管理器(最优雅的方式是注册为一个Spring Bean):
 * 1. 优点
 * - 1) 每个分组{@code group}中的线程默认都是单例的, 防止出现在方法内部循环创建线程池的错误写法;
 * - 2) 线程以{@code '${group}-${number}'}形式命名, 使在查看线程栈时更加清晰;
 * - 3) 开放{@code newFixedMinMaxThreadPool()}方法, 提供比{@code Executors}更灵活, 比{@code ThreadPoolExecutor}更便捷的配置方式;
 * - 4) 提供{@code com.alibaba.jbox.executor.policy}线程拒绝策略, 在{@code RunnableQueue}满时打印日志;
 * - 5) 添加{@code ExecutorMonitor}监控: 将线程池监控日志打印到{@code 'executor-monitor'}这个{@code Logger}下, 打印内容包含:
 * -- 5.a) 线程组信息
 * -- 5.b) 线程总数
 * -- 5.c) 活跃线程数
 * -- 5.d) 被阻塞的任务描述(在RunnableQueue内的任务的描述), 以及实例code
 * -- 5.e) 队列尚余空间
 * 2. 封装{@code Runnable}为{@code AsyncRunnable}, 为提交的任务增加描述信息:
 * 3. 如果将{@code ExecutorManager}注册为SpringBean, 会在应用关闭时自动将线程池关闭掉, 防止线程池未关导致应用下线不成功的bug.
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.3
 * @since 2017/1/16 14:15:00.
 */
public class ExecutorManager implements ExecutorLoggerInter {

    private static volatile boolean syncInvoke = false;

    static final ConcurrentMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    static final Map<String, AtomicLong> counters = new HashMap<>();

    // ---- * ThreadPoolExecutor * ---- //

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize,
                                                           int runnableQueueSize) {
        RejectedExecutionHandler rejectHandler = new CallerRunsPolicy(group);

        return newFixedMinMaxThreadPool(group, minPoolSize, maxPoolSize, runnableQueueSize, rejectHandler);
    }

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize,
                                                           int runnableQueueSize,
                                                           RejectedExecutionHandler rejectHandler) {
        return executors.computeIfAbsent(group, (key) -> {
            BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(runnableQueueSize);

            ThreadFactory threadFactory = new NamedThreadFactory(group);

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize,
                ScheduleTask.HALF_AN_HOUR_INTERVAL, TimeUnit.MILLISECONDS,
                runnableQueue,
                threadFactory,
                rejectHandler);

            return createExecutorProxy(group, threadPoolExecutor, ExecutorService.class);
        });
    }

    // ---- * newCachedThreadPool * ---- //
    public static ExecutorService newCachedThreadPool(String group) {
        return executors.computeIfAbsent(group, (key) -> {
            ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory(group));
            return createExecutorProxy(group, executor, ExecutorService.class);
        });
    }

    // ---- * newFixedThreadPool * ---- //
    public static ExecutorService newFixedThreadPool(String group, int poolSize) {
        return executors.computeIfAbsent(group, (key) -> {
            ExecutorService executor = Executors.newFixedThreadPool(poolSize, new NamedThreadFactory(group));
            return createExecutorProxy(group, executor, ExecutorService.class);
        });
    }

    // ---- * newScheduledThreadPool * ---- //
    public static ScheduledExecutorService newScheduledThreadPool(String group, int corePoolSize) {
        return (ScheduledExecutorService)executors.computeIfAbsent(group, (key) -> {

            ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize,
                new NamedThreadFactory(group));

            return createExecutorProxy(group, executor, ScheduledExecutorService.class);
        });
    }

    // ---- * newSingleThreadExecutor * ---- //
    public static ExecutorService newSingleThreadExecutor(String group) {
        return executors.computeIfAbsent(group, (key) -> {
            ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(group));
            return createExecutorProxy(group, executor, ExecutorService.class);
        });
    }

    // ---- * newSingleThreadScheduledExecutor * ---- //
    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String group) {
        return (ScheduledExecutorService)executors.computeIfAbsent(group, (key) -> {

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory(group));

            return createExecutorProxy(group, executor, ScheduledExecutorService.class);
        });
    }

    private static <T> T createExecutorProxy(String group, ExecutorService target, Class<T> interfaceType) {
        if (syncInvoke && !(target instanceof ScheduledExecutorService)) {
            target.shutdownNow();
            target = new SyncInvokeExecutorService();
        }

        return interfaceType.cast(
            Proxy.newProxyInstance(
                interfaceType.getClass().getClassLoader(),
                new Class[] {interfaceType},
                new RunnableDecoratorInterceptor(group, target)
            )
        );
    }

    public static void setSyncInvoke(boolean syncInvoke) {
        ExecutorManager.syncInvoke = syncInvoke;
    }

    @PreDestroy
    public void destroy() {
        executors.entrySet().stream()
            .filter(entry -> !entry.getValue().isShutdown())
            .forEach(entry -> {
                entry.getValue().shutdown();
                monitor.info("executor [{}] is shutdown", entry.getKey());
                logger.info("executor [{}] is shutdown", entry.getKey());
            });
    }
}
