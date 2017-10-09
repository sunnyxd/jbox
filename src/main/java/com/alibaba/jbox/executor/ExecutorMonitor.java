package com.alibaba.jbox.executor;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.text.DecimalFormat;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.alibaba.jbox.executor.ExecutorManager.FlightRecorder;
import com.alibaba.jbox.scheduler.ScheduleTask;
import com.alibaba.jbox.scheduler.TaskScheduler;
import com.alibaba.jbox.spring.AbstractApplicationContextAware;
import com.alibaba.jbox.stream.StreamForker;
import com.alibaba.jbox.utils.JboxUtils;
import com.alibaba.jbox.utils.ProxyUtil;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.util.ReflectionUtils;

import static com.alibaba.jbox.executor.ExecutorManager.executors;
import static com.alibaba.jbox.executor.ExecutorManager.recorders;
import static com.alibaba.jbox.utils.JboxUtils.getUsableBeanName;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_SINGLETON;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.5
 * @since 2017/8/22 15:32:00.
 */
public class ExecutorMonitor extends AbstractApplicationContextAware
    implements ScheduleTask, ExecutorLoggerInter, BeanDefinitionRegistryPostProcessor {

    private static Map<String, AtomicLong> beforeInvoked = new HashMap<>();

    private static final String ASYNC_KEY = "async";

    private static final String FUTURE_KEY = "future";

    private static final String CALLABLE_KEY = "callable";

    private long period = _1M_INTERVAL;

    @Override
    public void invoke() throws Exception {
        List<Entry<String, ExecutorService>> entrySet = executors.entrySet()
            .stream()
            .filter(entry -> !(entry.getValue() instanceof SyncInvokeExecutorService))
            .sorted((e1, e2) -> e2.getKey().length() - e1.getKey().length())
            .collect(Collectors.toList());

        StringBuilder logBuilder = new StringBuilder(128);
        // append group size:
        logBuilder.append("executors group [").append(entrySet.size()).append("]:\n");
        for (Map.Entry<String, ExecutorService> entry : entrySet) {
            String group = entry.getKey();
            ThreadPoolExecutor executor = getThreadPoolExecutor(entry.getValue());
            if (executor == null) {
                continue;
            }

            // append group detail:
            BlockingQueue<Runnable> queue = executor.getQueue();
            Object[] flightRecorder = getFlightRecorder(group);
            logBuilder.append(String.format(
                "%-33s > pool:[%s], active:[%d], core:[%d], max:[%d], "
                    + "success:[%s], failure:[%s], "
                    + "rt:[%s], tps:[%s], "
                    + "queues:[%d], remain:[%d]\n",
                "'" + group + "'",
                /*
                 *  pool detail
                 */
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),

                /*
                 * success, failure
                 */
                numberFormat(flightRecorder[0]),
                numberFormat(flightRecorder[1]),

                /*
                 * rt, tps
                 */
                String.format("%.2f", (double)flightRecorder[2]),
                numberFormat(calcTps(group, (long)flightRecorder[3])),

                /*
                 * runnable queue
                 */
                queue.size(),
                queue.remainingCapacity()));

            // append task detail:
            StringBuilder[] taskDetailBuilder = getTaskDetailBuilder(queue);
            for (StringBuilder sb : taskDetailBuilder) {
                logBuilder.append(sb);
            }
        }

        monitor.info(logBuilder.toString());
    }

    private long calcTps(String group, long invoked) {
        long before = beforeInvoked.computeIfAbsent(group, (k) -> new AtomicLong(0L))
            .getAndSet(invoked);
        return (long)((invoked - before) / passedSeconds());
    }

    private double passedSeconds() {
        return period * 1.0 / _1S_INTERVAL;
    }

    private Object[] getFlightRecorder(String group) {
        FlightRecorder recorder = recorders.getOrDefault(group, new FlightRecorder());
        long success = recorder.getSuccess().get();
        long failure = recorder.getFailure().get();
        double rt;
        if (success == 0 && failure == 0) {
            rt = 0.0;
        } else {
            rt = recorder.getTotalRt().get() * 1.0 / (success + failure);
        }

        return new Object[] {success, failure, rt, success + failure};
    }

    private StringBuilder[] getTaskDetailBuilder(BlockingQueue<Runnable> queue) {
        StreamForker<Runnable> forker = new StreamForker<>(queue.stream())
            .fork(ASYNC_KEY, stream -> stream
                .filter(runnable -> runnable instanceof AsyncRunnable)
                .collect(new Collector()))
            .fork(CALLABLE_KEY, stream -> stream
                .filter(callable -> callable instanceof AsyncCallable)
                .collect(new Collector()))
            .fork(FUTURE_KEY, stream -> stream
                .filter(runnable -> runnable instanceof FutureTask)
                .map(this::getFutureTaskInnerAsyncObject)
                .collect(new Collector()));

        StreamForker.Results results = forker.getResults();
        StringBuilder asyncLogBuilder = results.get(ASYNC_KEY);
        StringBuilder callableLogBuilder = results.get(CALLABLE_KEY);
        StringBuilder futureLogBuilder = results.get(FUTURE_KEY);

        return new StringBuilder[] {asyncLogBuilder, callableLogBuilder, futureLogBuilder};
    }

    /**
     * @param executorProxy
     * @return
     * @since 1.1
     */
    private ThreadPoolExecutor getThreadPoolExecutor(ExecutorService executorProxy) {
        ThreadPoolExecutor executor = null;

        if (executorProxy instanceof ThreadPoolExecutor) {
            executor = (ThreadPoolExecutor)executorProxy;
        } else if (Proxy.isProxyClass(executorProxy.getClass())) {
            Object target = ProxyUtil.getProxyTarget(executorProxy);
            if (target instanceof ThreadPoolExecutor) {
                executor = (ThreadPoolExecutor)target;
            } else if (target instanceof SyncInvokeExecutorService) {
                executor = null;
            } else {
                executor = (ThreadPoolExecutor)JboxUtils.getFieldValue(target, "e");
            }
        }

        return executor;
    }

    /**
     * @since 1.2
     */
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

    /**
     * @param registry
     * @throws BeansException
     * @since 1.4
     */
    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        Map<String, TaskScheduler> beans = applicationContext.getBeansOfType(TaskScheduler.class);
        if (beans == null || beans.isEmpty()) {
            RootBeanDefinition taskScheduler = new RootBeanDefinition(TaskScheduler.class);
            taskScheduler.setInitMethodName("start");
            taskScheduler.setDestroyMethodName("shutdown");
            taskScheduler.setScope(SCOPE_SINGLETON);
            taskScheduler.getConstructorArgumentValues().addIndexedArgumentValue(0, true);
            registry.registerBeanDefinition(getUsableBeanName("com.alibaba.jbox.scheduler.TaskScheduler", registry),
                taskScheduler);
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        beanFactory.getBean(TaskScheduler.class).register(this);
    }

    private String numberFormat(Object obj) {
        return new DecimalFormat("##,###").format(obj);
    }

    public void setPeriod(long period) {
        this.period = period;
    }
}
