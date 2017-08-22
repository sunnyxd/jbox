package com.alibaba.jbox.utils;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * @author jifang
 * @since 16/8/18 下午6:09.
 */
public class JboxUtils {

    public static Method getRealMethod(JoinPoint pjp) throws NoSuchMethodException {
        MethodSignature ms = (MethodSignature) pjp.getSignature();
        Method method = ms.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            method = pjp.getTarget().getClass().getDeclaredMethod(ms.getName(), method.getParameterTypes());
        }
        return method;
    }

    public static String getStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace != null && stackTrace.length > 2) {
            StringBuilder sb = new StringBuilder("current thread [")
                    .append(Thread.currentThread().getName())
                    .append("]'s stack trace: ");

            for (int i = 2 /*trim Thread.getStackTrace() & JboxUtils.getStackTrace() */; i < stackTrace.length; ++i) {
                sb.append("\n\t").append(stackTrace[i]);
            }

            return sb.toString();
        }

        return "";
    }
}
