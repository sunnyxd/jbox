package com.alibaba.jbox;

import com.alibaba.jbox.domain.Address;
import com.alibaba.jbox.domain.People;
import com.alibaba.jbox.utils.Collections3;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author jifang
 * @since 2017/3/1 下午2:39.
 */
public class Collection3Test {

    List<People> list;

    {
        Address a1 = new Address("a1", 3.14, 3.14);
        Address a2 = new Address("b2", 3.24, 3.24);
        Address a3 = new Address("c3", 3.34, 3.34);

        list = Arrays.asList(
                new People("fq1", a1),
                new People("fq2", a2),
                new People("fq3", a3),
                new People("fq1", a2));
    }

    @Test
    public void testGetFieldList() throws NoSuchFieldException {
        List<String> names = Collections3.mapListExclude(list, "name", "fq1");
        System.out.println(names);
    }

    @Test
    public void testList2Map() throws NoSuchFieldException {
        Map<String, Address> map = Collections3.groupList(list, "name", "address");
        System.out.println(map);
    }
}
