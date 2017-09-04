package com.alibaba.jbox;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.jbox.domain.Address;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jifang.zjf
 * @since 2017/7/26 下午9:20.
 */
public class DiamondTest {

    private Map<String, Address> list;

    @Test
    public void test1() throws NoSuchMethodException {
        Method valueOf = Test1.class.getDeclaredMethod("a", List.class);
        System.out.println(valueOf.toGenericString());
    }

    @Test
    public void test() throws NoSuchFieldException {
        Map<String, Address> map = new HashMap<>();
        map.put("nihao", new Address("1", 1.2, 1.3));
        String json = JSONObject.toJSONString(map);
        Field field = DiamondTest.class.getDeclaredField("list");
        field.setAccessible(true);
        Class<?> type = field.getType();
        Type genericType = field.getGenericType();

        Object object = JSON.parseObject(json, genericType);
        System.out.println(object);

        //Object json1 = DiamondPropertySourcesPlaceholder.convertTypeValue(json, List.class);
        //System.out.println(json1);
    }
}
