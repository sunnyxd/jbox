package com.alibaba.jbox.thread;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jifang
 * @since 2017/1/16 下午5:39.
 */
interface LoggerInter {

    Logger LOGGER = LoggerFactory.getLogger("com.alibaba.jbox.thread");

    Logger MONITOR_LOGGER = LoggerFactory.getLogger("thread-pool-monitor");
}
