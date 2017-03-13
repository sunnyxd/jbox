package com.vdian.jbox.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author jifang
 * @since 2017/2/23 下午3:50.
 */
public class Converters {

    @SuppressWarnings("unchecked")
    public static <Input, Output> List<Output> convert(Collection<Input> inputs, Function<Input, Output> function) {
        List outputs;
        if (inputs == null || inputs.isEmpty()) {
            outputs = Collections.emptyList();
        } else {
            outputs = new ArrayList(inputs.size());
            for (Input input : inputs) {
                outputs.add(function.convert(input));
            }
        }

        return (List<Output>) outputs;
    }

    public interface Function<Input, Output> {
        Output convert(Input input);
    }
}
