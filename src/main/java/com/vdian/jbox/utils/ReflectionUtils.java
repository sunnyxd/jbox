package com.vdian.jbox.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author jifang
 * @since 16/9/18 下午4:46.
 */
public class ReflectionUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionUtils.class);

    public static <T, U> List<U> getFieldValueList(Collection<T> originalList, String prop) {
        return getFieldValueList(originalList, prop, null);
    }

    @SuppressWarnings("unchecked")
    public static <T, U> List<U> getFieldValueList(Collection<T> originalList, String prop, U excludeVal) {
        List<U> result = new ArrayList<>();
        if (originalList != null && !originalList.isEmpty()) {

            Field field;
            try {
                field = originalList.toArray()[0].getClass().getDeclaredField(prop);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                return result;
            }

            for (T item : originalList) {
                try {
                    U value = (U) field.get(item);
                    if (value == null || (excludeVal != null && value.equals(excludeVal))) {
                        continue;
                    }
                    result.add(value);
                } catch (Exception e) {
                    LOGGER.error("get field failed, prop:" + prop, e);
                }
            }
        }
        return result;
    }

    public static <T, U> List<U> getFieldValueList2(Collection<T> originalList, String outerProp, String innerProp) {
        return getFieldValueList2(originalList, outerProp, innerProp, null);
    }

    @SuppressWarnings("unchecked")
    public static <T, U> List<U> getFieldValueList2(Collection<T> originalList, String outerProp, String innerProp, U excludeVal) {
        List<U> result = new ArrayList<>();
        if (originalList != null && !originalList.isEmpty()) {

            Field outerField;
            Field innerFiled;
            try {
                Object itemValue = originalList.toArray()[0];
                outerField = itemValue.getClass().getDeclaredField(outerProp);
                outerField.setAccessible(true);

                innerFiled = outerField.get(itemValue).getClass().getDeclaredField(innerProp);
                innerFiled.setAccessible(true);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return result;
            }

            for (T item : originalList) {
                try {
                    Object outerValue = outerField.get(item);
                    if (outerValue != null) {
                        U innerValue = (U) innerFiled.get(outerValue);
                        if (innerValue == null || (excludeVal != null && excludeVal.equals(innerValue))) {
                            continue;
                        }

                        result.add(innerValue);
                    }
                } catch (Exception e) {
                    LOGGER.error("get field failed, outterProp:" + outerProp, e);
                }
            }
        }

        return result;
    }


    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> list2Map(Collection originalList, String keyProp, String valueProp) {

        Map<K, V> map = new HashMap<>();

        if (originalList != null && !originalList.isEmpty()) {

            Field keyField;
            Field valueField;
            try {
                Class<?> originalClass = originalList.toArray()[0].getClass();
                keyField = originalClass.getDeclaredField(keyProp);
                keyField.setAccessible(true);

                valueField = originalClass.getDeclaredField(valueProp);
                valueField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                return map;
            }

            for (Object item : originalList) {
                try {
                    K k = (K) keyField.get(item);
                    V v = (V) valueField.get(item);
                    map.put(k, v);
                } catch (Exception e) {
                    LOGGER.error("list to map failed.", e);
                }
            }
        }
        return map;
    }

    @SuppressWarnings("unchecked")
    public static <K, V> Map<K, V> list2Map(Collection<V> originalList, String keyProp) {

        Map<K, V> map = new HashMap<>();

        if (originalList != null && !originalList.isEmpty()) {

            Field field;
            try {
                field = originalList.toArray()[0].getClass().getDeclaredField(keyProp);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                return map;
            }

            for (V item : originalList) {
                try {
                    K k = (K) field.get(item);
                    map.put(k, item);
                } catch (Exception e) {
                    LOGGER.error("list to map failed.", e);
                }
            }
        }
        return map;
    }


    public static void main(String[] args) {

        class Address {
            private String local;

            private double lat;

            private double lng;

            public Address(String local, double lat, double lng) {
                this.lat = lat;
                this.lng = lng;
                this.local = local;
            }
        }

        class People {
            private String name;
            private Address address;

            public People(String name, Address address) {
                this.address = address;
                this.name = name;
            }
        }

        Address a1 = new Address("a1", 3.14, 3.14);
        Address a2 = new Address("b2", 3.24, 3.24);
        Address a3 = new Address("c3", 3.34, 3.34);

        List<People> list = Arrays.asList(
                new People("fq1", a1),
                new People("fq2", a2),
                new People("fq3", a3));


//        List<String> adds = getFieldValueList2(list, "address", "local", "a1");
//        System.out.println(adds);

//        Map<String, Address> objectObjectMap = list2Map(list, "name", "address");
        Map<String, People> objectObjectMap = list2Map(list, "name");
        System.out.println(objectObjectMap);
    }
}