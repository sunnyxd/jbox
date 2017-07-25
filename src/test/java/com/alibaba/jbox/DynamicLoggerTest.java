package com.alibaba.jbox;

import com.alibaba.jbox.logger.AbstractLoggerConfigurator;
import com.alibaba.jbox.logger.Log4jConfigurator;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * @author jifang.zjf
 * @since 2017/7/23 下午7:22.
 */
public class DynamicLoggerTest {

    private static final Logger logger = LoggerFactory.getLogger(DynamicLoggerTest.class);

    private AbstractLoggerConfigurator configurator = new Log4jConfigurator();

    @Test
    public void test() {
        while (true) {
            MDC.put("traceId", "traceId: " + System.currentTimeMillis());
            if (logger.isWarnEnabled()) {
                logger.warn("info1: {}", "warn");
            }
            if (logger.isErrorEnabled()) {
                logger.error("info2: {}", "error");
            }
            Util.delay(1000);
        }
    }
}
