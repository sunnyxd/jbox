package com.alibaba.jbox;

import org.slf4j.*;
import org.slf4j.impl.StaticLoggerBinder;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/9/22 19:49:00.
 */
public class YamlTester {

    public static void main(String[] args) {
        ILoggerFactory loggerFactory = StaticLoggerBinder.getSingleton().getLoggerFactory();

        org.slf4j.Logger logger = LoggerFactory.getLogger("ss");
    }
}
