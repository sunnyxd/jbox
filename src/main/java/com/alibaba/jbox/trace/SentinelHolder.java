package com.alibaba.jbox.trace;

import com.taobao.csp.sentinel.Entry;
import com.taobao.csp.sentinel.SphU;
import com.taobao.csp.sentinel.slots.block.BlockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/3 22:17:00.
 */
class SentinelHolder {

    private static final Logger rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private static SentinelHolder instance;

    static SentinelHolder getInstance() {
        if (instance == null) {
            synchronized (SentinelHolder.class) {
                if (instance == null) {
                    instance = new SentinelHolder();
                }
            }
        }

        return instance;
    }

    void entry(Method method) throws BlockException {
        Entry entry = null;
        try {
            if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isProtected(method.getModifiers())) {
                entry = SphU.entry(method);
            }
        } catch (BlockException e) {
            rootLogger.warn("method: [{}] invoke was blocked by sentinel.", method.getName());
            throw e;
        } finally {
            if (entry != null) {
                entry.exit();
            }
        }
    }

}
