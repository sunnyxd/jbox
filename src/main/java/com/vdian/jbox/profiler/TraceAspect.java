package com.vdian.jbox.profiler;

import com.vdian.jbox.utils.JboxUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * @author jifang
 * @since 2016/11/25 上午11:53.
 */
@Aspect
public class TraceAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger("com.vdian.jbox.profiler");

    private static final Logger ROOT_LOGGER = LoggerFactory.getLogger("ROOT");

    @Around("@annotation(com.vdian.jbox.profiler.Trace)")
    public Object invoke(final ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            ROOT_LOGGER.error("", e);
            throw e;
        }

        Method method = JboxUtils.getRealMethod(joinPoint);
        Trace trace = method.getAnnotation(Trace.class);
        long cost = System.currentTimeMillis() - start;
        if (cost >= trace.value()) { // record
            onTimeOut(cost, joinPoint.getTarget(), method, joinPoint.getArgs());
        }

        return result;
    }

    protected void onTimeOut(long constTime, Object target, Method method, Object[] args) {
        String clazzName = method.getDeclaringClass().getName();
        String methodName = method.getName();
        String message = String.format("method: %s.%s invoke total cost %sms, params=%s",
                clazzName,
                methodName,
                constTime,
                Arrays.asList(args));

        LOGGER.warn(message);
    }
}
