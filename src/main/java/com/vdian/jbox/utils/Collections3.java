package com.vdian.jbox.utils;

import com.vdian.jbox.annotation.NotEmpty;
import com.vdian.jbox.annotation.NotNull;

import java.lang.reflect.Field;
import java.util.*;

/**
 * @author jifang
 * @since 16/9/18 下午4:46.
 */
@SuppressWarnings("unchecked")
public class Collections3 {

    /*******
     * getFieldList
     *******/
    public static <T, U> List<U> getFieldList(Collection<T> collection, String field)
            throws NoSuchFieldException {
        return getFieldListWithoutExclude(collection, field, null);
    }

    public static <T, U> List<U> getFieldList(Collection<T> collection, String outerField, String innerField)
            throws NoSuchFieldException {
        return getFieldListWithoutExclude(collection, outerField, innerField, null);
    }

    public static <T, U> List<U> getFieldListWithoutExclude(final Collection<T> collection, final String field, final U exclude)
            throws NoSuchFieldException {

        return listInit(collection, new ListFunction() {

            @Override
            public List process(Collection collection) throws NoSuchFieldException {

                List<U> list = new ArrayList<>(collection.size());
                Field fieldObj = getCollectionField(collection, field);

                for (Object item : collection) {
                    try {
                        U value = (U) fieldObj.get(item);

                        // ignore exclude
                        if (value.equals(exclude)) {
                            continue;
                        }

                        list.add(value);
                    } catch (IllegalAccessException ignored) {
                        // impossibility
                    }
                }

                return list;
            }
        });
    }

    public static <T, U> List<U> getFieldListWithoutExclude(final Collection<T> collection, final String outerField, final String innerField, final U exclude)
            throws NoSuchFieldException {

        return listInit(collection, new ListFunction() {

            @Override
            public List process(Collection collection) throws NoSuchFieldException {
                List<U> result = new ArrayList<>(collection.size());
                Field outerFieldObj = getCollectionField(collection, outerField);
                Field innerFiledObj = outerFieldObj.getType().getDeclaredField(innerField);
                innerFiledObj.setAccessible(true);

                for (Object item : collection) {
                    try {
                        Object outerValue = outerFieldObj.get(item);
                        if (outerValue != null) {
                            U innerValue = (U) innerFiledObj.get(outerValue);
                            if (innerValue.equals(exclude)) {
                                continue;
                            }

                            result.add(innerValue);
                        }
                    } catch (IllegalAccessException ignored) {
                        // impossibility
                    }
                }

                return result;
            }
        });
    }

    private static List listInit(Collection list, ListFunction function) throws NoSuchFieldException {
        List result;
        if (list == null || list.isEmpty()) {
            result = Collections.emptyList();
        } else {
            result = function.process(list);
        }

        return result;
    }

    /*******
     * list2Map
     *******/
    public static <K, V> Map<K, V> list2Map(Collection<V> collection, final String keyField)
            throws NoSuchFieldException {

        return mapInit(collection, new MapFunction() {

            @Override
            public Map process(Collection collection) throws NoSuchFieldException {
                Map<K, V> map = new HashMap<>(collection.size());

                Field field = getCollectionField(collection, keyField);
                for (Object item : collection) {
                    try {
                        K key = (K) field.get(item);
                        map.put(key, (V) item);
                    } catch (IllegalAccessException ignored) {
                        // impossibility
                    }
                }

                return map;
            }
        });
    }

    public static <K, V> Map<K, V> list2Map(Collection collection, final String keyField, final String valueField)
            throws NoSuchFieldException {

        return mapInit(collection, new MapFunction() {

            @Override
            public Map process(Collection collection) throws NoSuchFieldException {
                Map<K, V> map = new HashMap<>(collection.size());

                Field keyFieldObj = getCollectionField(collection, keyField);
                Field valueFieldObj = getCollectionField(collection, valueField);

                for (Object item : collection) {
                    try {
                        K k = (K) keyFieldObj.get(item);
                        V v = (V) valueFieldObj.get(item);
                        map.put(k, v);
                    } catch (IllegalAccessException ignored) {
                        // impossibility
                    }
                }

                return map;
            }
        });
    }


    /*******
     * list2MultiMap
     *******/
    public static <K, V> Map<K, List<V>> list2MultiMap(Collection<V> collection, final String keyField)
            throws NoSuchFieldException {

        return mapInit(collection, new MapFunction() {

            @Override
            public Map process(Collection collection) throws NoSuchFieldException {
                Map<K, List<V>> map = new HashMap<>();
                Field field = getCollectionField(collection, keyField);

                for (Object item : collection) {
                    try {
                        K key = (K) field.get(item);
                        List<V> vs = map.get(key);
                        if (vs == null) {
                            vs = new ArrayList<>();
                            map.put(key, vs);
                        }
                        vs.add((V) item);
                    } catch (IllegalAccessException e) {
                        // impossibility
                    }
                }

                return map;
            }
        });
    }

    public static <K, V> Map<K, List<V>> list2MultiMap(Collection collection, final String keyField, final String valueFiled)
            throws NoSuchFieldException {

        return mapInit(collection, new MapFunction() {

            @Override
            public Map process(Collection list) throws NoSuchFieldException {
                Map<K, List<V>> map = new HashMap<>();
                Field keyFieldObj = getCollectionField(list, keyField);
                Field valueFiledObj = getCollectionField(list, valueFiled);

                for (Object item : list) {
                    try {
                        K key = (K) keyFieldObj.get(item);
                        List<V> vs = map.get(key);
                        if (vs == null) {
                            vs = new ArrayList<>();
                            map.put(key, vs);
                        }
                        vs.add((V) valueFiledObj.get(item));
                    } catch (IllegalAccessException e) {
                        // impossibility
                    }
                }

                return map;
            }
        });
    }

    private static Map mapInit(Collection list, MapFunction function) throws NoSuchFieldException {
        Map map;
        if (list == null || list.isEmpty()) {
            map = Collections.emptyMap();
        } else {
            map = function.process(list);
        }

        return map;
    }

    private static Field getCollectionField(@NotNull @NotEmpty Collection collection, String fieldName) throws NoSuchFieldException {
        Field field = collection.toArray()[0]
                .getClass()
                .getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }

    private interface MapFunction {
        Map process(Collection list) throws NoSuchFieldException;
    }

    private interface ListFunction {
        List process(Collection list) throws NoSuchFieldException;
    }
}