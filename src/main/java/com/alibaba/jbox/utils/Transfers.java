package com.alibaba.jbox.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * @author jifang.zjf@alibaba-inc.com
 * @since 2017/2/23 下午3:50.
 */
public class Transfers {

    @SuppressWarnings("unchecked")
    public static <Input, Output> List<Output> transfer(Collection<Input> inputs, Function<Input, Output> function) {
        List outputs;
        if (inputs == null || inputs.isEmpty()) {
            outputs = Collections.emptyList();
        } else {
            outputs = new ArrayList(inputs.size());
            for (Input input : inputs) {
                outputs.add(function.apply(input));
            }
        }

        return (List<Output>)outputs;
    }
}
