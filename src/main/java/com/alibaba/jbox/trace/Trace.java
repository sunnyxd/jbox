package com.alibaba.jbox.trace;

import java.lang.annotation.*;

/**
 * @author jifang
 * @since 2016/11/25 上午11:51.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Trace {
    long value() default 200L;
}