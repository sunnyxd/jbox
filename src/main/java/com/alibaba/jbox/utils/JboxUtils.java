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
}
