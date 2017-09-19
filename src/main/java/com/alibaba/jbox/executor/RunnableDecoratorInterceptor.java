package com.alibaba.jbox.executor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author jifang@alibaba-inc.com
 * @version 1.1
 * @since 2017/1/16 下午3:42.
 */
class RunnableDecoratorInterceptor implements InvocationHandler {

    private Object target;

    RunnableDecoratorInterceptor(Object target) {
        this.target = target;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // process param args
        if (isNeedProxy(method, args)) {
            Runnable runnable = (Runnable)args[0];
            RunnableDecorator decorator = new RunnableDecorator(runnable);
            args[0] = decorator;
        }

        return method.invoke(target, args);
    }

    /**
     * proxy:
     * 1. ${@code void execute(Runnable command); }
     * 2. ${@code Future<T> submit(Runnable task); }
     * 3. ${@code Future<T> submit(Runnable task, T result); }
     * 4. ${@code ScheduledFuture<T> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit)}
     * 5. ${@code ScheduledFuture<T> schedule(Runnable command, long delay, TimeUnit unit)}
     *
     * @param method
     * @param arguments
     * @return
     */
    private boolean isNeedProxy(Method method, Object[] arguments) {
        String methodName = method.getName();
        Class<?>[] pTypes = method.getParameterTypes();

        return needProxyMethods.contains(methodName)
            && pTypes.length != 0
            && Runnable.class.isAssignableFrom(pTypes[0])
            && arguments[0] instanceof Runnable
            && !(arguments[0] instanceof AsyncRunnable);
    }

    private static final List<String> needProxyMethods = Arrays.asList(
        "scheduleAtFixedRate",
        "execute",
        "submit",
        "schedule"
    );
}
