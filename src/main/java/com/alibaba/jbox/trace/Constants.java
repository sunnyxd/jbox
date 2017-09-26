package com.alibaba.jbox.trace;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/23 07:55:00.
 */
interface Constants {

    String TLOG_EXECUTOR_GROUP = "com.alibaba.jbox:TLogManager";

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
}
