package com.alibaba.jbox.spring;

import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import java.beans.PropertyDescriptor;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.1
 * @since 2017/7/15 07:03:00.
 */
public class BeanInstantiationMonitor implements InstantiationAwareBeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private static final int DEFAULT_TOP = 10;

    private static final Logger logger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);

    private static final PriorityQueue<Triple<String, String, Long>> queue = new PriorityQueue<>((o1, o2) -> (int) (o2.getRight() - o1.getRight()));

    private static final AtomicLong totalCost = new AtomicLong(0L);

    private static final ConcurrentMap<String, ThreadLocal<Long>> threadLocals = new ConcurrentHashMap<>();

    private volatile int top = DEFAULT_TOP;

    private volatile boolean detail = false;

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
        threadLocals.computeIfPresent(beanName, (k, threadLocal) -> {
            String beanType = bean.getClass().getName();
            long cost = System.currentTimeMillis() - threadLocal.get();
            totalCost.addAndGet(cost);
            queue.offer(Triple.of(beanName, beanType, cost));

            if (detail) {
                String message = String.format(" -> bean:'%s' of type [%s] init cost: [%s] ms", beanName, beanType, cost);
                logger.info(message);
            }
            return null;
        });

        return bean;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() == null) {
            int top = Math.min(queue.size(), this.top);
            StringBuilder msgBuilder = new StringBuilder(1000);
            msgBuilder
                    .append("application '")
                    .append(System.getProperty("project.name", "unnamed"))
                    .append("' context init total cost: [")
                    .append(totalCost.get())
                    .append("] ms,");
            if (!detail) {
                msgBuilder.append(" set 'BeanInstantiationMonitor.detail = true', show bean init cost detail,");
            }

            msgBuilder.append(" top ")
                    .append(top)
                    .append(": \n");


            for (int i = 1; i <= top; ++i) {
                Triple<String, String, Long> triple = queue.poll();
                msgBuilder
                        .append(i < 10 ? "  " : " ")
                        .append(i)
                        .append(". bean:'")
                        .append(triple.getLeft())
                        .append("', type [")
                        .append(triple.getMiddle())
                        .append("], cost: [")
                        .append(String.format("%.2f", triple.getRight() * 1.0 / 1000))
                        .append("]s\n");
            }

            threadLocals.clear();
            queue.clear();
            logger.warn(msgBuilder.toString());
        }
    }

    public void setTop(int top) {
        this.top = top;
    }

    public void setDetail(boolean detail) {
        this.detail = detail;
    }
}
