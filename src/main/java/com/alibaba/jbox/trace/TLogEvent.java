package com.alibaba.jbox.trace;

import com.alibaba.jbox.utils.JboxUtils;

import com.taobao.eagleeye.EagleEye;
import com.taobao.hsf.util.RequestCtxUtil;
import lombok.Data;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 15:57:00.
 */
@Data
class TLogEvent {

    /**
     * obtain from context
     */
    private String serverIp;

    private String traceId;

    private String clientName;

    private String clientIp;

    private String invokeThread;

    /**
     * need generate from TraceAspect invoke
     */
    private long invokeTime;

    private String className;

    private String methodName;

    private Object[] args;

    private long costTime;

    private Object result;

    private Throwable exception;

    void init() {
        this.serverIp = JboxUtils.getServerIp();
        this.traceId = EagleEye.getTraceId();
        this.clientName = RequestCtxUtil.getAppNameOfClient();
        this.clientIp = RequestCtxUtil.getClientIp();
        this.invokeThread = Thread.currentThread().getName();
    }
}
