package com.alibaba.jbox.trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 07:55:00.
 */
interface TraceConstants {

    Logger tracer = LoggerFactory.getLogger("com.alibaba.jbox.trace");

    String TLOG_EXECUTOR_GROUP = "com.alibaba.jbox:TLogManager";

    /**
     * append {@code %X{traceId}} in logback/log4j MDC.
     */
    String TRACE_ID = "traceId";

    String CONFIG_KEY_PATTERN = "%s:%s";

    String LOG_SUFFIX = ".log";

    int DEFAULT_MAX_HISTORY = 15;

    int MAX_THREAD_POOL_SIZE = 12;

    int MIN_THREAD_POOL_SIZE = 3;

    int DEFAULT_RUNNABLE_Q_SIZE = 1024;

    String SEPARATOR = "|";

    String PLACEHOLDER = "";

    String UTF_8 = "UTF-8";

    String KEY_ARGS = "args";

    String KEY_RESULT = "result";

    String KEY_PLACEHOLDER = "placeholder";

    /**
     * Abbreviations for placeholder.
     */
    String KEY_PH = "ph";

    String USER_DEF = "def:";

    String DEF_PREFIX = "${";

    String DEF_SUFFIX = "}";

    String REF_PREFIX = "#{";

    String REF_SUFFIX = "}";

    String JSON_FILE_SUFFIX = ".json";

    String XML_FILE_SUFFIX = ".xml";

    String GROOVY_FILE_SUFFIX = ".groovy";

    String LOGGER_FILE_PATTERN = ".%d{yyyy-MM-dd}.gz";
}
