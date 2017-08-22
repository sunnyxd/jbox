package com.alibaba.jbox.utils;

import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/22 16:21:00.
 */
public class ProxyUtil {

    public static Object getProxyTarget(Object proxy) {
        Object target = AopTargetUtils.softReferenceMap.get(proxy);
        if (target == null) {
            try {
                Field hField = proxy.getClass().getSuperclass().getDeclaredField("h");
                ReflectionUtils.makeAccessible(hField);
                Object h = hField.get(proxy);

                assert h != null;
                Field targetField = h.getClass().getDeclaredField("target");
                ReflectionUtils.makeAccessible(targetField);
                target = targetField.get(h);

                AopTargetUtils.softReferenceMap.put(proxy, target);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        return target;
    }
}
