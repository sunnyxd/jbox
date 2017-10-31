package com.alibaba.jbox.caces.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

import com.alibaba.jbox.caces.service.HelloWorldService;
import com.alibaba.jbox.domain.User;
import com.alibaba.jbox.domain.UserWrapper;
import com.alibaba.jbox.executor.AsyncCallable;
import com.alibaba.jbox.executor.ExecutorManager;
import com.alibaba.jbox.trace.Trace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Service;

/**
 * Created by jifang.zjf
 * Since 2017/5/9 上午9:34.
 */
@Service("helloWorldServiceImpl")
public class HelloWorldServiceImpl implements HelloWorldService, InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(HelloWorldService.class);

    public HelloWorldServiceImpl() {
        System.out.println("construct");
    }

    @Trace
    @Override
    public String sayHello(String name) {
        return "hello : " + name;
    }

    @Trace
    @Override
    public User user(User user) throws Exception {
        return user;
    }

    @Trace
    @Override
    public List<User> users(List<User> users) {
        return users;
    }

    @Trace
    @Override
    public List<String> strs(String[] strs) {
        ExecutorService single = ExecutorManager.newSingleThreadExecutor("single");
        single.submit(new AsyncCallable<List<String>>() {
            @Override
            public List<String> execute() throws Exception {
                logger.info("strs: {}", strs);
                return Collections.emptyList();
            }
        });
        return Collections.emptyList();
    }

    @Override
    public void userWrapper(UserWrapper wrapper) {
        // throw new RuntimeException(wrapper.toString());
    }

    @Trace
    @Override
    public void limit(@Min(1) int page, @Max(50) int size) {
        ExecutorService cached = ExecutorManager.newCachedThreadPool("cached");
        cached.submit(new AsyncCallable<Object>() {
            @Override
            public Object execute() {
                logger.info("page: {}, limit:{}", page, size);
                throw new RuntimeException("hh");
            }
        });
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        System.out.println("afterPropertiesSet");
    }

    @PostConstruct
    public void setUp() {
        System.out.println("@PostConstruct");
    }
}
