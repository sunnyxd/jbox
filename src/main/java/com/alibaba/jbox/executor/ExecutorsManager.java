package com.alibaba.jbox.executor;

import com.alibaba.jbox.scheduler.ScheduleTask;
import org.apache.commons.proxy.ProxyFactory;

import javax.annotation.PreDestroy;
import java.util.concurrent.*;

/**
 * 线程池管理器(最优雅的方式是注册为一个Spring Bean):
 * 1. 优点
 * - 1) 每个分组{@code group}中的线程默认都是单例的, 防止出现在方法内部循环创建线程池的错误写法;
 * - 2) 线程以{@code group-n}形式命名, 使在查看线程栈时更加清晰;
 * - 3) 开放{@code newFixedMinMaxThreadPool()}方法, 提供比{@code Executors}更灵活, 比{@code ThreadPoolExecutor}更便捷的配置方式;
 * - 4) 提供{@code LoggedCallerRunsPolicy}的线程拒绝策略, 在{@code RunnableQueue}满时打印日志;
 * - 5) 添加{@code ExecutorsMonitor}监控: 将线程池监控日志打印到{@code 'executor-monitor'}这个{@code Logger}下, 打印内容包含:
 * -- 5.a) 线程组信息
 * -- 5.b) 线程总数
 * -- 5.c) 活跃线程数
 * -- 5.d). 被阻塞的任务描述(在RunnableQueue内的任务的描述)
 * 2. 封装{@code Runnable}为{@code AsyncRunnable}, 为提交的任务增加描述信息:
 * 3. 如果将{@code ExecutorsManager}注册为SpringBean, 会在应用关闭时自动将线程池关闭掉, 防止线程池未关导致应用下线不成功的bug.
 *
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.2
 * @since 2017/1/16 14:15:00.
 */
public class ExecutorsManager implements LoggerInter {

    static final ConcurrentMap<String, ExecutorService> executors = new ConcurrentHashMap<>();

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runnableQueueSize) {
        RejectedExecutionHandler rejectHandler = new LoggedCallerRunsPolicy(group);

        return newFixedMinMaxThreadPool(group, minPoolSize, maxPoolSize, runnableQueueSize, rejectHandler);
    }

    public static ExecutorService newFixedMinMaxThreadPool(String group, int minPoolSize, int maxPoolSize, int runnableQueueSize, RejectedExecutionHandler rejectHandler) {
        return executors.computeIfAbsent(group, (key) -> {
            BlockingQueue<Runnable> runnableQueue = new ArrayBlockingQueue<>(runnableQueueSize);

            ThreadFactory threadFactory = new NamedThreadFactory(group);

            ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(minPoolSize, maxPoolSize,
                    ScheduleTask.HALF_AN_HOUR_INTERVAL, TimeUnit.MILLISECONDS,
                    runnableQueue,
                    threadFactory,
                    rejectHandler);

            return (ExecutorService) createExecutorProxy(threadPoolExecutor, ExecutorService.class);
        });
    }

    public static ExecutorService newFixedThreadPool(String group, int poolSize) {
        return executors.computeIfAbsent(group, (key) -> {

            ExecutorService executor = Executors.newFixedThreadPool(poolSize, new NamedThreadFactory(group));

            return (ExecutorService) createExecutorProxy(executor, ExecutorService.class);
        });
    }

    public static ExecutorService newCachedThreadPool(String group) {
        return executors.computeIfAbsent(group, (key) -> {
            ExecutorService executor = Executors.newCachedThreadPool(new NamedThreadFactory(group));

            return (ExecutorService) createExecutorProxy(executor, ExecutorService.class);
        });
    }

    public static ExecutorService newSingleThreadExecutor(String group) {
        return executors.computeIfAbsent(group, (key) -> {

            ExecutorService executor = Executors.newSingleThreadExecutor(new NamedThreadFactory(group));

            return (ExecutorService) createExecutorProxy(executor, ExecutorService.class);
        });
    }

    public static ScheduledExecutorService newScheduledThreadPool(String group, int corePoolSize) {
        return (ScheduledExecutorService) executors.computeIfAbsent(group, (key) -> {

            ScheduledExecutorService executor = Executors.newScheduledThreadPool(corePoolSize, new NamedThreadFactory(group));

            return (ScheduledExecutorService) createExecutorProxy(executor, ScheduledExecutorService.class);
        });
    }

    public static ScheduledExecutorService newSingleThreadScheduledExecutor(String group) {
        return (ScheduledExecutorService) executors.computeIfAbsent(group, (key) -> {

            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory(group));

            return (ScheduledExecutorService) createExecutorProxy(executor, ScheduledExecutorService.class);
        });
    }

    private static Object createExecutorProxy(Object executor, Class<?> type) {

        return new ProxyFactory().createInterceptorProxy(
                executor,
                new RunnableDecoratorInterceptor(),
                new Class[]{type}
        );
    }

    @PreDestroy
    public void destroy() {
        executors.entrySet().stream()
                .filter(entry -> !entry.getValue().isShutdown())
                .forEach(entry -> {
                    entry.getValue().shutdown();
                    monitorLogger.info("executor [{}] is shutdown", entry.getKey());
                    logger.info("executor [{}] is shutdown", entry.getKey());
                });
    }
}
