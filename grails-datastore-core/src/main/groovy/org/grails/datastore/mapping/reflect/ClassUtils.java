/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.reflect;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Helper methods for dealing with classes and reflection
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class ClassUtils {
    public static final Map<Class<?>, Class<?>> PRIMITIVE_TYPE_COMPATIBLE_CLASSES = new HashMap<Class<?>, Class<?>>();

    /**
     * Just add two entries to the class compatibility map
     * @param left
     * @param right
     */
    private static final void registerPrimitiveClassPair(Class<?> left, Class<?> right) {
        PRIMITIVE_TYPE_COMPATIBLE_CLASSES.put(left, right);
        PRIMITIVE_TYPE_COMPATIBLE_CLASSES.put(right, left);
    }

    static {
        registerPrimitiveClassPair(Boolean.class, boolean.class);
        registerPrimitiveClassPair(Integer.class, int.class);
        registerPrimitiveClassPair(Short.class, short.class);
        registerPrimitiveClassPair(Byte.class, byte.class);
        registerPrimitiveClassPair(Character.class, char.class);
        registerPrimitiveClassPair(Long.class, long.class);
        registerPrimitiveClassPair(Float.class, float.class);
        registerPrimitiveClassPair(Double.class, double.class);
    }

    /**
     * Determine whether the {@link Class} identified by the supplied name is present
     * and can be loaded. Will return {@code false} if either the class or
     * one of its dependencies is not present or cannot be loaded.
     * @param className the name of the class to check
     * (may be {@code null}, which indicates the default class loader)
     * @return whether the specified class is present
     */
    public static boolean isPresent(String className) {
        try {
            Class.forName(className, false, ClassUtils.class.getClassLoader());
            return true;
        }
        catch (Throwable ex) {
            // Class or one of its dependencies is not present...
            return false;
        }
    }

    /**
     * Returns true if the specified clazz parameter is either the same as, or is a superclass or superinterface
     * of, the specified type parameter. Converts primitive types to compatible class automatically.
     *
     * @param clazz
     * @param type
     * @return true if the class is a taglib
     * @see java.lang.Class#isAssignableFrom(Class)
     */
    public static boolean isAssignableOrConvertibleFrom(Class<?> clazz, Class<?> type) {
        if (type == null || clazz == null) {
            return false;
        }
        if (type.isPrimitive()) {
            // convert primitive type to compatible class
            Class<?> primitiveClass = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(type);
            if (primitiveClass == null) {
                // no compatible class found for primitive type
                return false;
            }
            return clazz.isAssignableFrom(primitiveClass);
        }
        else if(type.isArray() && Iterable.class.isAssignableFrom(clazz)) {
            return true;
        }
        return clazz.isAssignableFrom(type);
    }
    /**
     * Determine whether the {@link Class} identified by the supplied name is present
     * and can be loaded. Will return {@code false} if either the class or
     * one of its dependencies is not present or cannot be loaded.
     * @param className the name of the class to check
     * @param classLoader the class loader to use
     * (may be {@code null}, which indicates the default class loader)
     * @return whether the specified class is present
     */
    public static boolean isPresent(String className, ClassLoader classLoader) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        }
        catch (Throwable ex) {
            // Class or one of its dependencies is not present...
            return false;
        }
    }

    /**
     * Retrieves a boolean value from a Map for the given key
     *
     * @param key The key that references the boolean value
     * @param map The map to look in
     * @return A boolean value which will be false if the map is null, the map doesn't contain the key or the value is false
     */
    public static boolean getBooleanFromMap(String key, Map<?, ?> map) {
        if (map == null) return false;
        if (map.containsKey(key)) {
            Object o = map.get(key);
            if (o == null)return false;
            if (o instanceof Boolean) {
                return (Boolean)o;
            }
            return Boolean.valueOf(o.toString());
        }
        return false;
    }

    public static boolean isClassBelowPackage(Class<?> domainClazz, List packageList) {
        String classPackage = domainClazz.getPackage().getName();
        for (Object packageName : packageList) {
            if (packageName != null) {
                if (classPackage.startsWith(packageName.toString())) {
                    return true;
                }
            }
        }
        return false;

    }

    /**
     * Whether the class is multi tenant
     *
     * @param clazz The class
     * @return True if it is
     */
    public static boolean isMultiTenant(Class clazz) {
        Class<?>[] allInterfacesForClass = org.springframework.util.ClassUtils.getAllInterfacesForClass(clazz);
        for (Class anInterface : allInterfacesForClass) {
            if(anInterface.getSimpleName().equals("MultiTenant")) {
                return true;
            }
        }
        return false;
    }
}
