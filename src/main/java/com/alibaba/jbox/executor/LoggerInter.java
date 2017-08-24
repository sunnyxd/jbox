package com.alibaba.jbox.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang
 * @since 2017/1/16 下午5:39.
 */
interface LoggerInter {

    Logger logger = LoggerFactory.getLogger("com.alibaba.jbox.executor");

    Logger monitorLogger = LoggerFactory.getLogger("executors-monitor");
}
