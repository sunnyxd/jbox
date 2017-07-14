package com.alibaba.jbox.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author jifang.zjf
 * @since 2017/7/14 下午6:07.
 */
public class BeanInitializationLogger implements BeanPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BeanInitializationLogger.class);

    private static final ConcurrentMap<String, ThreadLocal<Long>> threadLocals = new ConcurrentHashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ThreadLocal<Long> threadLocal = new ThreadLocal<>();
        threadLocal.set(System.currentTimeMillis());
        threadLocals.putIfAbsent(beanName, threadLocal);

        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        ThreadLocal<Long> threadLocal = threadLocals.get(beanName);
        logger.info(" -> bean:'{}' of type [{}] init cost: [{}] ms", beanName, bean.getClass().getName(), (System.currentTimeMillis() - threadLocal.get()));
        return bean;
    }
}