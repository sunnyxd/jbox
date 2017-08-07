package com.alibaba.jbox.trace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jifang
 * @since 2016/11/25 上午11:51.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Trace {

    boolean value() default true;

    boolean logger() default false;

    String name() default "";

    long threshold() default Long.MAX_VALUE;
}
