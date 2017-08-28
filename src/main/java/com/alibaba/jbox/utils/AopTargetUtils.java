package com.alibaba.jbox.utils;

import org.springframework.aop.framework.AdvisedSupport;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.ConcurrentReferenceHashMap;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentMap;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/22 15:47:00.
 */
public class AopTargetUtils {

    static final ConcurrentMap<Object, Object> softReferenceMap = new ConcurrentReferenceHashMap<>();

    public static Object getTarget(Object bean) throws Exception {
        Object target = softReferenceMap.get(bean);

        if (target == null) {
            if (AopUtils.isAopProxy(bean)) {
                target = AopUtils.isJdkDynamicProxy(bean) ? getJDKProxyTarget(bean) : getCglibProxyTarget(bean);
            } else {
                target = bean;
            }
            softReferenceMap.put(bean, target);
        }

        return target;
    }

    private static Object getJDKProxyTarget(Object proxy) throws Exception {
        Field h = proxy.getClass().getSuperclass().getDeclaredField("h");
        h.setAccessible(true);
        AopProxy aopProxy = (AopProxy) h.get(proxy);

        Field advised = aopProxy.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        return ((AdvisedSupport) advised.get(aopProxy)).getTargetSource().getTarget();
    }

    private static Object getCglibProxyTarget(Object proxy) throws Exception {
        Field h = proxy.getClass().getDeclaredField("CGLIB$CALLBACK_0");
        h.setAccessible(true);
        Object dynamicAdvisedInterceptor = h.get(proxy);

        Field advised = dynamicAdvisedInterceptor.getClass().getDeclaredField("advised");
        advised.setAccessible(true);

        return ((AdvisedSupport) advised.get(dynamicAdvisedInterceptor)).getTargetSource().getTarget();
    }
}

