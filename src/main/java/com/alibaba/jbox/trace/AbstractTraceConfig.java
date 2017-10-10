package com.alibaba.jbox.trace;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/28 13:23:00.
 */
@Data
public abstract class AbstractTraceConfig implements Serializable {

    private static final long serialVersionUID = 8892376403020670231L;

    /**
     * use for replace @Trace when not use Trace annotation.
     * methodKey:${ClassImplName:MethodName} -> TraceConfig
     */
    private ConcurrentMap<String, TraceConfig> traceConfigs = new ConcurrentHashMap<>();

    /**
     * used when method's implementation class has non default logger instance.
     */
    private Logger defaultBizLogger;

    /**
     * determine 'validate arguments' use or not.
     */
    private volatile boolean validator = false;

    /**
     * determine 'log method invoke cost time' use or not.
     */
    private volatile boolean elapsed = false;

    /**
     * determine the 'log method invoke cost time' append method param or not.
     */
    private volatile boolean param = true;

    /**
     * determine the 'log method invoke cost time' append method invoke result or not.
     */
    private volatile boolean result = true;

    /**
     * determine use sentinel for 'rate limit'.
     */
    private volatile boolean sentinel = false;

    /**
     * determine append 'com.alibaba.jbox.trace' log or not.
     */
    private volatile boolean trace = false;

    /**
     * determine append root error log.
     */
    private volatile boolean errorRoot = false;

    /**
     * use for push TLog event.
     */
    private List<TLogManager> tLogManagers;

    public void setBizLoggerName(String bizLoggerName) {
        this.defaultBizLogger = LoggerFactory.getLogger(bizLoggerName);
    }

    public void settLogManager(TLogManager tLogManager) {
        if (tLogManagers == null) {
            tLogManagers = new CopyOnWriteArrayList<>();
        }
        tLogManagers.add(tLogManager);
    }

    public List<TLogManager> getTLogManagers() {
        if (tLogManagers == null) {
            return Collections.emptyList();
        }
        return tLogManagers;
    }
}