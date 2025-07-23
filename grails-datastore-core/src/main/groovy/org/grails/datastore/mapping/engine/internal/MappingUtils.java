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
package org.grails.datastore.mapping.engine.internal;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.SortedSet;
import java.util.TreeSet;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.PropertyMapping;

/**
 * Utility methods for mapping logic.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class MappingUtils {
    private static final String PROPERTY_SET_PREFIX = "set";
    private static final String PROPERTY_GET_PREFIX = "get";

    /**
     * Retrieves the name of a setter for the specified property name
     * @param propertyName The property name
     * @return The setter equivalent
     */
    public static String getSetterName(String propertyName) {
        final String suffix = getSuffixForGetterOrSetter(propertyName);
        return PROPERTY_SET_PREFIX+suffix;
    }

    /**
     * Calculate the name for a getter method to retrieve the specified property
     * @param propertyName
     * @return The name for the getter method for this property, if it were to exist, i.e. getConstraints
     */
    public static String getGetterName(String propertyName) {
        final String suffix = getSuffixForGetterOrSetter(propertyName);
        return PROPERTY_GET_PREFIX + suffix;
    }

    private static String getSuffixForGetterOrSetter(String propertyName) {
        final String suffix;
        if (propertyName.length() > 1 &&
                Character.isLowerCase(propertyName.charAt(0)) &&
                Character.isUpperCase(propertyName.charAt(1))) {
            suffix = propertyName;
        } else {
            suffix = Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
        }
        return suffix;
    }

    public static String getTargetKey(PersistentProperty property) {
        PropertyMapping<Property> mapping = property.getMapping();
        String targetName;

        if (mapping != null && mapping.getMappedForm() != null) {
            String tmp = mapping.getMappedForm().getTargetName();
            targetName = tmp != null ? tmp : property.getName();
        }
        else {
            targetName = property.getName();
        }
        return targetName;
    }

    /**
     * Creates a concrete collection for the supplied interface
     * @param interfaceType The interface
     * @return ArrayList for List, TreeSet for SortedSet, LinkedHashSet for Set etc.
     */
    public static Collection createConcreteCollection(Class interfaceType) {
        Collection elements;
        if (interfaceType.equals(List.class)) {
            elements = new ArrayList();
        }
        else if (interfaceType.equals(SortedSet.class)) {
            elements = new TreeSet();
        }
        else if (interfaceType.equals(Queue.class)) {
            elements = new ArrayDeque();
        }
        else {
            elements = new LinkedHashSet();
        }
        return elements;
    }

    /**
     * Get a declared field, searching super classes for the field if it is not found in the class.
     * @param javaClass The class to search.
     * @param propertyName The name of the field.
     * @return The field, or null if it couldn't be found.
     */
    public static Field getDeclaredField(Class javaClass, String propertyName) {
        while (javaClass != null) {
            Field[] declaredFields = javaClass.getDeclaredFields();
            for (Field declaredField : declaredFields) {
                if (declaredField.getName().equals(propertyName)) {
                    return declaredField;
                }
            }
            javaClass = javaClass.getSuperclass();
        }
        return null;
    }

    public static Class getGenericTypeForProperty(Class javaClass, String propertyName) {
        Class genericClass = null;

        Field declaredField = getDeclaredField(javaClass, propertyName);
        if(declaredField != null) {
            Class<?> type = declaredField.getType();
            Type genericType = declaredField.getGenericType();
            if (genericType instanceof ParameterizedType) {
                Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
                int len = typeArguments.length;
                if (len >0) {
                    int i = 0;
                    if(Map.class.isAssignableFrom(type) && len == 2) {
                        i++;
                    }
                    Type typeArg = typeArguments[i];
                    if(typeArg instanceof Class) {
                        genericClass = (Class) typeArg;
                    }
                }
            }
        }
        return genericClass;
    }

    public static Class getGenericTypeForMapProperty(Class javaClass, String propertyName, boolean isKeyType) {
        Class genericClass = null;

        Field declaredField = getDeclaredField(javaClass, propertyName);
        Type genericType = declaredField != null ? declaredField.getGenericType() : null;
        if (genericType instanceof ParameterizedType) {
            Type[] typeArguments = ((ParameterizedType) genericType).getActualTypeArguments();
            if (typeArguments.length>0) {
                Type typeArg = typeArguments[isKeyType ? 0 : 1];
                if(typeArg instanceof Class) {
                    genericClass = (Class) typeArg;
                }
            }
        }
        return genericClass;
    }

    public static Class getGenericType(Class propertyType) {
        Class genericType = null;
        TypeVariable[] typeParameters = propertyType.getTypeParameters();
        if (typeParameters != null && typeParameters.length>0) {
            Type[] bounds = typeParameters[0].getBounds();
            if (bounds != null && bounds.length>0 && (bounds[0] instanceof Class)) {
                genericType = (Class) bounds[0];
            }
        }
        return genericType;
    }
}
