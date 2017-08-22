package com.alibaba.jbox.executor;

import org.apache.commons.proxy.Interceptor;
import org.apache.commons.proxy.Invocation;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * @author jifang
 * @since 2017/1/16 下午3:42.
 */
class RunnableDecoratorInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {

        Object result;

        Object[] arguments = invocation.getArguments();
        Method method = invocation.getMethod();
        Object target = invocation.getProxy();
        if (isNeedProxy(method, arguments)) {
            Runnable runnable = (Runnable) arguments[0];
            RunnableDecorator decorator = new RunnableDecorator(runnable);

            arguments[0] = decorator;

            result = method.invoke(target, arguments);
        } else {
            result = invocation.proceed();
        }

        return result;
    }

    /**
     * proxy:
     * 1. ${@code void execute(Runnable command); }
     * 2. ${@code Future<?> submit(Runnable task); }
     * 3. ${@code Future<T> submit(Runnable task, T result); }
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
