package com.alibaba.jbox.executor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

import com.alibaba.jbox.executor.ExecutorManager.Pair;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import com.taobao.eagleeye.EagleEye;
import lombok.NonNull;
import org.slf4j.MDC;

import static com.alibaba.jbox.executor.ExecutorManager.counters;

/**
 * @author jifang@alibaba-inc.com
 * @version 1.1
 * @since 2017/1/16 下午3:42.
 */
class RunnableDecoratorInterceptor implements InvocationHandler {

    private String group;

    private ExecutorService target;

    RunnableDecoratorInterceptor(String group, ExecutorService target) {
        this.group = group;
        this.target = target;
    }

    private static final Set<String> NEED_PROXY_METHODS = Sets.newConcurrentHashSet(Arrays.asList(
        "execute",
        "submit",
        "schedule",
        "scheduleAtFixedRate",
        "scheduleWithFixedDelay"
    ));

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        if (NEED_PROXY_METHODS.contains(methodName)) {
            Object rpcContext = EagleEye.getRpcContext();
            Object firstArg = args[0];
            Pair<AtomicLong, AtomicLong> counter = counters.computeIfAbsent(group,
                (k) -> new Pair<>(new AtomicLong(0L), new AtomicLong(0L)));

            if (firstArg instanceof AsyncRunnable) {
                args[0] = new AsyncRunnableDecorator(counter, (AsyncRunnable)firstArg, rpcContext);
            } else if (firstArg instanceof AsyncCallable) {
                args[0] = new AsyncCallableDecorator(counter, (AsyncCallable)firstArg, rpcContext);
            } else if (firstArg instanceof Runnable) {
                args[0] = new RunnableDecorator(counter, (Runnable)firstArg, rpcContext);
            } else if (firstArg instanceof Callable) {
                args[0] = new CallableDecorator(counter, (Callable)firstArg, rpcContext);
            }
        }

        return method.invoke(target, args);
    }
}

class RunnableDecorator implements AsyncRunnable {

    private Pair<AtomicLong, AtomicLong> counter;

    private Runnable runnable;

    private Object rpcContext;

    RunnableDecorator(Pair<AtomicLong, AtomicLong> counter, @NonNull Runnable runnable, Object rpcContext) {
        this.counter = counter;
        this.runnable = runnable;
        this.rpcContext = rpcContext;
    }

    @Override
    public void run() {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            runnable.run();
            counter.getLeft().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            counter.getRight().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return runnable.getClass().getName();
    }

    @Override
    public void execute() { }
}

class CallableDecorator implements AsyncCallable {

    private Pair<AtomicLong, AtomicLong> counter;

    private Callable callable;

    private Object rpcContext;

    CallableDecorator(Pair<AtomicLong, AtomicLong> counter, @NonNull Callable callable, Object rpcContext) {
        this.counter = counter;
        this.callable = callable;
        this.rpcContext = rpcContext;
    }

    @Override
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            Object result = callable.call();
            counter.getLeft().incrementAndGet();
            return result;

        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            counter.getRight().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return callable.getClass().getName();
    }

    @Override
    public Object execute() throws Exception {
        return null;
    }
}

class AsyncRunnableDecorator implements AsyncRunnable {

    private Pair<AtomicLong, AtomicLong> counter;

    private AsyncRunnable asyncRunnable;

    private Object rpcContext;

    AsyncRunnableDecorator(Pair<AtomicLong, AtomicLong> counter, @NonNull AsyncRunnable asyncRunnable,
                           Object rpcContext) {
        this.counter = counter;
        this.asyncRunnable = asyncRunnable;
        this.rpcContext = rpcContext;
    }

    @Override
    public void run() {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            asyncRunnable.execute();
            counter.getLeft().incrementAndGet();
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            counter.getRight().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return asyncRunnable.taskInfo();
    }

    @Override
    public void execute() { }
}

class AsyncCallableDecorator implements AsyncCallable {

    private Pair<AtomicLong, AtomicLong> counter;

    private AsyncCallable asyncCallable;

    private Object rpcContext;

    AsyncCallableDecorator(Pair<AtomicLong, AtomicLong> counter, @NonNull AsyncCallable asyncCallable,
                           Object rpcContext) {
        this.counter = counter;
        this.asyncCallable = asyncCallable;
        this.rpcContext = rpcContext;
    }

    @Override
    public Object call() throws Exception {
        EagleEye.setRpcContext(rpcContext);
        String traceId = EagleEye.getTraceId();
        if (!Strings.isNullOrEmpty(traceId)) {
            MDC.put(TRACE_ID, traceId);
        }
        try {
            Object result = asyncCallable.execute();
            counter.getLeft().incrementAndGet();
            return result;
        } catch (Throwable e) {
            logger.error("task: '{}' in thread: [{}] execute failed:", taskInfo(), Thread.currentThread().getName(), e);
            counter.getRight().incrementAndGet();
            throw e;
        } finally {
            if (!Strings.isNullOrEmpty(traceId)) {
                MDC.remove(TRACE_ID);
            }
            EagleEye.clearRpcContext();
        }
    }

    @Override
    public String taskInfo() {
        return asyncCallable.taskInfo();
    }

    @Override
    public Object execute() throws Exception {
        return null;
    }
}