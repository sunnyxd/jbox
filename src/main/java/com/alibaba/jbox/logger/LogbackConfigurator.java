package com.alibaba.jbox.logger;

import java.util.Collection;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.base.Strings;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;

/**
 * @author jifang.zjf
 * @since 2017/7/24 上午11:09.
 */
public class LogbackConfigurator extends AbstractLoggerConfigurator {

    @Override
    protected void handleLoggerConfigs(Map<String, String> loggerConfigs) {
        ILoggerFactory loggerFactory = LoggerFactory.getILoggerFactory();
        if (loggerFactory instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) loggerFactory;

            Collection<Logger> loggerList = loggerContext.getLoggerList();
            loggerList.forEach(logger -> {
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
}