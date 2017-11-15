package com.alibaba.jbox.trace;

import javax.annotation.Resource;

import com.alibaba.jbox.TestBase;
import com.alibaba.jbox.caces.service.HelloWorldService;

import org.junit.Test;
import org.springframework.context.support.FileSystemXmlApplicationContext;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @version 1.0
 * @since 2017/10/28 04:25:00.
 */
public class TLogCompositeTest extends TestBase {

    @Resource
    private HelloWorldService helloWorldService;

    @Test
    public void test() {
        //FileSystemXmlApplicationContext applicationContext = new FileSystemXmlApplicationContext(
        //    "/Users/jifang.zjf/IdeaProjects/jbox/src/test/java/resources/spring/applicationContext.xml");
        //
        //
        //HelloWorldService bean = applicationContext.getBean(HelloWorldService.class);


        helloWorldService.limit(1, 2);
    }
}
