package com.alibaba.jbox.trace;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/10/25 17:03:00.
 */
public interface TLogFilter {

    /**
     * determine the logMsg log or not.
     *
     * @param fmtLogMsg: 已经格式化好的Log信息
     */
    FilterReply decide(String fmtLogMsg);

    enum FilterReply {
        /**
         * DENY: the logMsg will be dropped.
         */
        DENY,
        /**
         * NEUTRAL: then the next filter, if any, will be invoked.
         */
        NEUTRAL,
        /**
         * ACCEPT: the logMsg will be logged without consulting with other filters in the chain.
         */
        ACCEPT
    }
}
