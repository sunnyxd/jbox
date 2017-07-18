package com.alibaba.jbox.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.beans.PropertyDescriptor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jifang.zjf
 * @since 2017/7/15 上午7:03.
 */
public class BeanInstantiationLogger implements InstantiationAwareBeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private static final Logger logger = LoggerFactory.getLogger(BeanInstantiationLogger.class);

    private static final AtomicLong totalCost = new AtomicLong(0L);

    private static final ConcurrentMap<String, ThreadLocal<Long>> threadLocals = new ConcurrentHashMap<>();

    @Override
    public Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
        long start = System.currentTimeMillis();
        threadLocals.put(beanName, ThreadLocal.withInitial(() -> start));

        return null;
    }

    @Override
    public boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
        return true;
    }

    @Override
    public PropertyValues postProcessPropertyValues(PropertyValues pvs, PropertyDescriptor[] pds, Object bean, String beanName) throws BeansException {
        return pvs;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        threadLocals.computeIfPresent(beanName, (instanceName, threadLocal) -> {
            long cost = System.currentTimeMillis() - threadLocal.get();
            totalCost.addAndGet(cost);

            String message = String.format(" -> bean:'%s' of type [%s] init cost: [%s] ms", instanceName, bean.getClass().getName(), cost);
            logger.info(message);
            return null;
        });

        return bean;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            String message = String.format(" -> application '%s' context init total cost: [%s] ms",
                    System.getProperty("project.name", "unnamed"),
                    totalCost.get());
            logger.info(message);
        }
    }
}
