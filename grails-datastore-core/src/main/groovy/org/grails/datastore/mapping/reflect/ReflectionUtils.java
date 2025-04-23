/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.mapping.reflect;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.grails.datastore.mapping.model.DatastoreConfigurationException;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;

/**
 * Provides methods to help with reflective operations
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ReflectionUtils {

    public static final Map<Class<?>, Class<?>> PRIMITIVE_TYPE_COMPATIBLE_CLASSES = new HashMap<Class<?>, Class<?>>();
    @SuppressWarnings("rawtypes")
    private static final Class[] EMPTY_CLASS_ARRAY = {};

    /**
     * Just add two entries to the class compatibility map
     * @param left
     * @param right
     */
    private static void registerPrimitiveClassPair(Class<?> left, Class<?> right) {
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
    * Make the given field accessible, explicitly setting it accessible if necessary.
    * The <code>setAccessible(true)</code> method is only called when actually necessary,
    * to avoid unnecessary conflicts with a JVM SecurityManager (if active).
    *
    * Based on the same method in Spring core.
    *
    * @param field the field to make accessible
    * @see java.lang.reflect.Field#setAccessible
    */
   public static void makeAccessible(Field field) {
       if (!Modifier.isPublic(field.getModifiers()) ||
               !Modifier.isPublic(field.getDeclaringClass().getModifiers())) {
           field.setAccessible(true);
       }
   }

    /**
     * Make the given method accessible, explicitly setting it accessible if necessary.
     * The <code>setAccessible(true)</code> method is only called when actually necessary,
     * to avoid unnecessary conflicts with a JVM SecurityManager (if active).
     *
     * Based on the same method in Spring core.
     *
     * @param method the method to make accessible
     * @see java.lang.reflect.Method#setAccessible
     */
    public static void makeAccessible(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) ||
                !Modifier.isPublic(method.getDeclaringClass().getModifiers())) {
            method.setAccessible(true);
        }
    }

    /**
     * <p>Tests whether or not the left hand type is compatible with the right hand type in Groovy
     * terms, i.e. can the left type be assigned a value of the right hand type in Groovy.</p>
     * <p>This handles Java primitive type equivalence and uses isAssignableFrom for all other types,
     * with a bit of magic for native types and polymorphism i.e. Number assigned an int.
     * If either parameter is null an exception is thrown</p>
     *
     * @param leftType The type of the left hand part of a notional assignment
     * @param rightType The type of the right hand part of a notional assignment
     * @return True if values of the right hand type can be assigned in Groovy to variables of the left hand type.
     */
    public static boolean isAssignableFrom(final Class<?> leftType, final Class<?> rightType) {
        if (leftType == null) {
            throw new NullPointerException("Left type is null!");
        }
        if (rightType == null) {
            throw new NullPointerException("Right type is null!");
        }
        if (leftType == Object.class) {
            return true;
        }
        if (leftType == rightType) {
            return true;
        }
        // check for primitive type equivalence
        Class<?> r = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(leftType);
        boolean result = r == rightType;

        if (!result) {
            // If no primitive <-> wrapper match, it may still be assignable
            // from polymorphic primitives i.e. Number -> int (AKA Integer)
            if (rightType.isPrimitive()) {
                // see if incompatible
                r = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(rightType);
                if (r != null) {
                    result = leftType.isAssignableFrom(r);
                }
            }
            else {
                // Otherwise it may just be assignable using normal Java polymorphism
                result = leftType.isAssignableFrom(rightType);
            }
        }
        return result;
    }

    /**
     * Instantiates an object catching any relevant exceptions and rethrowing as a runtime exception
     *
     * @param clazz The class
     * @return The instantiated object or null if the class parameter was null
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static <T> T instantiate(Class<T> clazz) {
        if (clazz == null) return null;
        try {
            return clazz.getConstructor(EMPTY_CLASS_ARRAY).newInstance();
        } catch (IllegalAccessException e) {
            throw new InstantiationException(e.getClass().getName() + " error creating instance of class ["+e.getMessage()+"]: " + e.getMessage(), e);
        } catch (InvocationTargetException e) {
            throw new InstantiationException(e.getClass().getName() + " error creating instance of class ["+e.getMessage()+"]: " + e.getMessage(), e);
        } catch (NoSuchMethodException e) {
            throw new InstantiationException(e.getClass().getName() + " error creating instance of class ["+e.getMessage()+"]: " + e.getMessage(), e);
        } catch (java.lang.InstantiationException e) {
            throw new InstantiationException(e.getClass().getName() + " error creating instance of class ["+e.getMessage()+"]: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves all the properties of the given class for the given type
     *
     * @param clazz The class to retrieve the properties from
     * @param propertyType The type of the properties you wish to retrieve
     *
     * @return An array of PropertyDescriptor instances
     */
    public static PropertyDescriptor[] getPropertiesOfType(Class<?> clazz, Class<?> propertyType) {
        if (clazz == null || propertyType == null) {
            return new PropertyDescriptor[0];
        }

        Set<PropertyDescriptor> properties = new HashSet<PropertyDescriptor>();
        try {
            for (PropertyDescriptor descriptor : BeanUtils.getPropertyDescriptors(clazz)) {
                Class<?> currentPropertyType = descriptor.getPropertyType();
                if (isTypeInstanceOfPropertyType(propertyType, currentPropertyType)) {
                    properties.add(descriptor);
                }
            }
        }
        catch (Exception e) {
            // if there are any errors in instantiating just return null for the moment
            return new PropertyDescriptor[0];
        }
        return properties.toArray(new PropertyDescriptor[properties.size()]);
    }

    private static boolean isTypeInstanceOfPropertyType(Class<?> type, Class<?> propertyType) {
        return propertyType.isAssignableFrom(type) && !propertyType.equals(Object.class);
    }

    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property
     *
     * @param name True if its a Javabean property
     * @param args The arguments
     * @return true if it is a javabean property method
     */
    public static boolean isGetter(String name, Class<?>[] args) {
        if (!StringUtils.hasText(name) || args == null)return false;
        if (args.length != 0)return false;

        if (name.startsWith("get")) {
            name = name.substring(3);
            if (name.length() > 0 && Character.isUpperCase(name.charAt(0))) return true;
        }
        else if (name.startsWith("is")) {
            name = name.substring(2);
            if (name.length() > 0 && Character.isUpperCase(name.charAt(0))) return true;
        }
        return false;
    }

    @SuppressWarnings("rawtypes")
    public static boolean isSetter(String name, Class[] args) {
        if (!StringUtils.hasText(name) || args == null)return false;

        if (name.startsWith("set")) {
            if (args.length != 1) return false;
            name = name.substring(3);
            if (name.length() > 0 && Character.isUpperCase(name.charAt(0))) return true;
        }

        return false;
    }

    public static Class forName(String className, ClassLoader classLoader) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new DatastoreConfigurationException("Class not found loading GORM: " + e.getMessage(), e);
        }
    }
}
