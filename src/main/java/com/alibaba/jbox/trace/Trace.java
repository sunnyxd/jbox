package com.alibaba.jbox.trace;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author jifang.zjf
 * @since  2016/11/25 11:51.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Trace {

    /**
     * open Trace monitoring or not.
     */
    boolean value() default true;

    /**
     * method invoke total cost threshold,
     * if ${method invoke cost time} > ${threshold} then append an 'cost time' log.
     */
    long threshold() default -1;

    /**
     * determine the 'cost time' logger used
     * (default use the only one 'org.slf4j.Logger' in this class)
     */
    String logger() default "";

    /**
     * append params in log or not.
     */
    boolean param() default false;
}
