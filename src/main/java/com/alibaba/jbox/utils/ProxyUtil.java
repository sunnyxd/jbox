package com.alibaba.jbox.utils;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/8/22 16:21:00.
 */
public class ProxyUtil {

    public static Object getProxyTarget(Object proxy) {
        return JboxUtils.getFieldValue(proxy, "h", "target");
    }
}
