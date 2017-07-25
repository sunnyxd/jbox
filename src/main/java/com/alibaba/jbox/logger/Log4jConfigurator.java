package com.alibaba.jbox.logger;

import com.google.common.base.Strings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.util.Collection;
import java.util.Map;

/**
 * @author jifang.zjf
 * @since 2017/7/24 上午11:15.
 */
public class Log4jConfigurator extends AbstractLoggerConfigurator {

    @Override
    protected void handleLoggerConfigs(Map<String, String> loggerConfigs) {
        LoggerContext loggerContext = LoggerContext.getContext();
        Collection<Logger> loggers = loggerContext.getLoggers();
        loggers.forEach(logger -> {
            String loggerName = logger.getName();
            String loggerValue = loggerConfigs.get(loggerName);

            if (!Strings.isNullOrEmpty(loggerValue)) {
                Level newLevel = Level.toLevel(loggerValue, logger.getLevel());
                if (!newLevel.equals(logger.getLevel())) {
                    logger.setLevel(newLevel);
                }
            }
        });
    }
}
