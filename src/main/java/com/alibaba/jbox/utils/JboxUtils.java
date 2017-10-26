package com.alibaba.jbox.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import lombok.NonNull;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.util.ReflectionUtils;

/**
 * @author jifang
 * @since 16/8/18 下午6:09.
 */
public class JboxUtils {

    private static final Logger logger = LoggerFactory.getLogger("com.alibaba.jbox");

    private static final String DEFAULT_LOCAL_IP = "127.0.0.1";

    public static final Object EMPTY_OBJ = new Object();

    private static final ConcurrentMap<Method, String> simplifiedNameMap = new ConcurrentHashMap<>();

    public static Object getFieldValue(@NonNull Object target, @NonNull String fieldName) {
        Field field = ReflectionUtils.findField(target.getClass(), fieldName);

        if (field == null) {
            return null;
        }

        ReflectionUtils.makeAccessible(field);
        return ReflectionUtils.getField(field, target);
    }

    public static Object getFieldValue(@NonNull Object target, @NonNull String outerFieldName,
                                       String... innerFieldNames) {
        Object outerTarget = getFieldValue(target, outerFieldName);

        Object innerObject = null;
        if (innerFieldNames.length != 0) {
            for (String innerFieldName : innerFieldNames) {
                if (outerTarget == null) {
                    break;
                }

                Field innerField = ReflectionUtils.findField(outerTarget.getClass(), innerFieldName);
                if (innerField == null) {
                    break;
                }

                ReflectionUtils.makeAccessible(innerField);
                innerObject = ReflectionUtils.getField(innerField, outerTarget);

                outerTarget = innerObject;
            }
        }

        return innerObject;
    }

    public static Method getAbstractMethod(JoinPoint pjp) {
        MethodSignature ms = (MethodSignature)pjp.getSignature();
        Method method = ms.getMethod();
        return method;
    }

    public static Method getImplMethod(JoinPoint pjp) throws NoSuchMethodException {
        MethodSignature ms = (MethodSignature)pjp.getSignature();
        Method method = ms.getMethod();
        if (method.getDeclaringClass().isInterface()) {
            method = pjp.getTarget().getClass().getDeclaredMethod(ms.getName(), method.getParameterTypes());
        }
        return method;
    }

    public static String getSimplifiedMethodName(Method method) {
        return simplifiedNameMap.computeIfAbsent(method, (m) -> {
            try {
                StringBuilder sb = new StringBuilder();

                specificToStringHeader(method, sb);

                sb.append('(');
                separateWithCommas(method.getParameterTypes(), sb);
                sb.append(')');
                if (method.getExceptionTypes().length > 0) {
                    sb.append(" throws ");
                    separateWithCommas(method.getExceptionTypes(), sb);
                }
                return sb.toString();
            } catch (Exception e) {
                return "<" + e + ">";
            }
        });
    }

    private static void specificToStringHeader(Method method, StringBuilder sb) {
        sb.append(trimName(method.getReturnType().getTypeName())).append(' ');
        sb.append(method.getDeclaringClass().getTypeName()).append(':');
        sb.append(method.getName());
    }

    private static void separateWithCommas(Class<?>[] types, StringBuilder sb) {
        for (int j = 0; j < types.length; j++) {
            sb.append(trimName(types[j].getTypeName()));
            if (j < (types.length - 1)) {
                sb.append(",");
            }
        }
    }

    private static String trimName(String name) {
        int index = name.lastIndexOf(".");
        if (index != -1) {
            name = name.substring(index + 1);
        }
        return name;
    }

    public static String getStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        if (stackTrace != null && stackTrace.length > 2) {
            StringBuilder sb = new StringBuilder("current thread [")
                .append(Thread.currentThread().getName())
                .append("] : ");

            for (int i = 2 /*trim Thread.getStackTrace() & JboxUtils.getStackTrace() */; i < stackTrace.length; ++i) {
                sb.append("\n\t").append(stackTrace[i]);
            }

            return sb.toString();
        }

        return "";
    }

    private static String serverIp = null;

    public static String getServerIp() {
        return serverIp == null ? (serverIp = serverIpSupplier.get()) : serverIp;
    }

    private static final Supplier<String> serverIpSupplier = () -> {
        String serverIp = null;
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            boolean fonded = false;
            while (!fonded && networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    if (inetAddress instanceof Inet4Address
                        && !inetAddress.isLoopbackAddress()
                        && !inetAddress.isSiteLocalAddress()) {

                        serverIp = inetAddress.getHostAddress();
                        fonded = true;
                        break;
                    }
                }
            }
        } catch (SocketException e) {
            logger.error("get local ip error:", e);
            serverIp = DEFAULT_LOCAL_IP;
        }

        return serverIp;
    };

    public static String trimPrefixAndSuffix(String value, String prefix, String suffix, boolean isNeedJudge) {
        if (!isNeedJudge) {
            return value.substring(prefix.length(), value.length() - suffix.length());
        } else {
            return trimPrefixAndSuffix(value, prefix, suffix);
        }
    }

    public static String trimPrefixAndSuffix(String value, String prefix, String suffix) {
        if (value.startsWith(prefix)) {
            value = value.substring(prefix.length());
        }
        if (value.endsWith(suffix)) {
            value = value.substring(0, value.length() - suffix.length());
        }

        return value;
    }

    /**
     * 获取一个可用的SpringBean name
     *
     * @param initBeanName : 初始bean name
     * @param registry     : spring bean策略
     * @return
     */
    public static String getUsableBeanName(String initBeanName, BeanDefinitionRegistry registry) {
        String beanName;
        int index = 0;
        do {
            beanName = initBeanName + "#" + index++;
        } while (registry.isBeanNameInUse(beanName));

        return beanName;
    }
}
