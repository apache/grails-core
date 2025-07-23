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

import groovy.lang.GroovySystem;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaClass;
import groovy.lang.MetaMethod;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.reflection.CachedMethod;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.runtime.metaclass.MultipleSetterProperty;
import org.springframework.beans.BeanUtils;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Reads the properties of a class in an optimized manner avoiding exceptions.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class ClassPropertyFetcher {

    private final Class clazz;
    private final ClassInfo classInfo;
    private final MetaClass theMetaClass;
    private final List<MetaProperty> metaProperties;
    public static final Set EXCLUDED_PROPERTIES = new HashSet(Arrays.asList("class", "metaClass", "properties"));

    public static ClassPropertyFetcher forClass(final Class c) {
        return new ClassPropertyFetcher(c);
    }

    /**
     * @deprecated Does nothing, no longer needed
     */
    @Deprecated
    public static void clearCache()  {
        // no-op
    }

    ClassPropertyFetcher(final Class clazz) {
        this.clazz = clazz;
        this.classInfo = ClassInfo.getClassInfo(clazz);
        this.theMetaClass = classInfo.getMetaClass();
        List<MetaProperty> properties = theMetaClass.getProperties();
        this.metaProperties = new ArrayList<>(properties.size());
        for (MetaProperty property : properties) {
            int modifiers = property.getModifiers();
            String propertyName = property.getName();
            if(!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || EXCLUDED_PROPERTIES.contains(propertyName)) {
                continue;
            }

            if(property instanceof MetaBeanProperty) {
                MetaBeanProperty beanProperty = (MetaBeanProperty) property;
                MetaMethod getter = beanProperty.getGetter();
                if(getter instanceof CachedMethod) {
                    this.metaProperties.add(property);
                }
            }
            else if(property instanceof MultipleSetterProperty) {
                MultipleSetterProperty msp = (MultipleSetterProperty) property;
                MetaMethod getter = msp.getGetter();
                if(getter instanceof CachedMethod) {
                    try {
                        CachedClass cachedClass = classInfo.getCachedClass();
                        Method foundGetter = clazz.getDeclaredMethod(NameUtils.getGetterName(propertyName));
                        if(foundGetter != null) {

                            getter = new CachedMethod(cachedClass, foundGetter);
                            Method foundSetter = clazz.getDeclaredMethod(NameUtils.getSetterName(propertyName), getter.getReturnType());
                            if(foundSetter != null) {
                                MetaMethod setter = new CachedMethod(cachedClass, foundSetter);
                                this.metaProperties.add(new MetaBeanProperty(msp.getName(), getter.getReturnType(), getter, setter));
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * @return The Java that this ClassPropertyFetcher was constructor for
     */
    public Class getJavaClass() {
        return clazz;
    }

    /**
     * @deprecated will be removed in a future version of GORM
     */
    @Deprecated
    public Object getReference() {
        return BeanUtils.instantiateClass(clazz);
    }

    /**
     * @deprecated  Use getMetaProperties instead
     */
    @Deprecated
    public PropertyDescriptor[] getPropertyDescriptors() {
        try {
            return Introspector.getBeanInfo(clazz).getPropertyDescriptors();
        } catch (IntrospectionException e) {
            return new PropertyDescriptor[0];
        }
    }

    /**
     * @return The meta properties of this class
     */
    public List<MetaProperty> getMetaProperties() {
        return metaProperties;
    }

    public boolean isReadableProperty(String name) {
        MetaProperty metaProperty = theMetaClass.getMetaProperty(name);
        if(metaProperty instanceof MetaBeanProperty) {
            MetaBeanProperty metaBeanProperty = (MetaBeanProperty) metaProperty;
            return metaBeanProperty.getField() != null || metaBeanProperty.getGetter() != null;
        }
        return false;
    }

    public Object getPropertyValue(String name) {
        MetaClass theMetaClass = this.theMetaClass;
        return getStaticPropertyValue(theMetaClass, name);
    }

    private static Object getStaticPropertyValue(MetaClass theMetaClass, String name) {
        MetaProperty metaProperty = theMetaClass.getMetaProperty(name);
        if(metaProperty != null && Modifier.isStatic(metaProperty.getModifiers())) {
            return metaProperty.getProperty(theMetaClass.getTheClass());
        }
        return null;
    }

    public Object getPropertyValue(final Object instance, String name) {
        MetaClass metaClass = this.theMetaClass;
        return getInstancePropertyValue(instance, name, metaClass);
    }

    public static Object getInstancePropertyValue(Object instance, String name) {
        return getInstancePropertyValue(instance, name, GroovySystem.getMetaClassRegistry().getMetaClass(instance.getClass()));
    }

    public <T> T getStaticPropertyValue(String name, Class<T> c) {
        return returnOnlyIfInstanceOf(getPropertyValue(name), c);
    }

    public static <T> T getStaticPropertyValue(Class clazz, String name, Class<T> requiredType) {
        return returnOnlyIfInstanceOf(getStaticPropertyValue(GroovySystem.getMetaClassRegistry().getMetaClass(clazz), name), requiredType);
    }

    /**
     * Get the list of property values for this static property from the whole inheritance hierarchy, starting
     * from the most derived version of the property ending with the base class. There are entries for each extant
     * version of the property in turn, so if you have a 10-deep inheritance hierarchy, you may get 0+ values returned,
     * one per class in the hierarchy that has the property declared (and of the correct type).
     * @param name Name of the property.
     * @param c Required type of the property (including derived types)
     * @param <T> Required type of the property.
     * @return The list, with 0+ values (never null). Do not modify the returned list.
     */
    public <T> List<T> getStaticPropertyValuesFromInheritanceHierarchy(String name, Class<T> c) {
        ClassInfo classInfo = this.classInfo;
        return getStaticPropertyValuesFromInheritanceHierarchy(classInfo, name, c);
    }

    /**
     * Get the list of property values for this static property from the whole inheritance hierarchy, starting
     * from the most derived version of the property ending with the base class. There are entries for each extant
     * version of the property in turn, so if you have a 10-deep inheritance hierarchy, you may get 0+ values returned,
     * one per class in the hierarchy that has the property declared (and of the correct type).
     * @param name Name of the property.
     * @param requiredTyped Required type of the property (including derived types)
     * @param <T> Required type of the property.
     * @return The list, with 0+ values (never null). Do not modify the returned list.
     */
    public static <T> List<T> getStaticPropertyValuesFromInheritanceHierarchy(Class theClass, String name, Class<T> requiredTyped) {
        return getStaticPropertyValuesFromInheritanceHierarchy(ClassInfo.getClassInfo(theClass), name, requiredTyped);
    }

    private static <T> List<T> getStaticPropertyValuesFromInheritanceHierarchy(ClassInfo classInfo, String name, Class<T> c) {
        CachedClass cachedClass = classInfo.getCachedClass();
        Collection<ClassInfo> hierarchy = cachedClass.getHierarchy();
        Class javaClass = cachedClass.getTheClass();
        List<T> values = new ArrayList<>(hierarchy.size());
        for (ClassInfo current : hierarchy) {
            if(cachedClass.isInterface()) continue;
            MetaProperty metaProperty = current.getMetaClass().getMetaProperty(name);
            if(metaProperty != null && Modifier.isStatic(metaProperty.getModifiers())) {
                Class type = metaProperty.getType();
                if(type == Object.class || c.isAssignableFrom(type)) {
                    if(metaProperty instanceof MetaBeanProperty) {
                        MetaBeanProperty beanProperty = (MetaBeanProperty) metaProperty;
                        CachedField field = beanProperty.getField();
                        // try the field
                        if(field != null) {
                            Object val = field.getProperty(javaClass);
                            if(c.isInstance(val)) {
                                values.add((T) val);
                            }
                        }
                        else {
                            Object val = metaProperty.getProperty(javaClass);
                            if(c.isInstance(val)) {
                                values.add((T) val);
                            }
                        }
                    }
                    else {
                        Object val = metaProperty.getProperty(javaClass);
                        if(c.isInstance(val)) {
                            values.add((T) val);
                        }
                    }
                }
                else {
                    // try the field
                    Field field = org.springframework.util.ReflectionUtils.findField(javaClass, name);
                    if (field != null && c.isAssignableFrom(field.getType())) {
                        org.springframework.util.ReflectionUtils.makeAccessible(field);
                        try {
                            values.add((T) field.get(javaClass));
                        } catch (IllegalAccessException ignored) {}
                    }
                    return null;
                }
            }
        }
        Collections.reverse(values);
        return values;
    }

    public <T> T getPropertyValue(String name, Class<T> c) {
        return getStaticPropertyValue(name, c);
    }

    public Class getPropertyType(String name) {
        return getPropertyType(name, false);
    }

    public Class getPropertyType(String name, boolean onlyInstanceProperties) {
        MetaProperty metaProperty = theMetaClass.getMetaProperty(name);
        if(metaProperty != null) {
            boolean isStatic = Modifier.isStatic(metaProperty.getModifiers());
            if(onlyInstanceProperties && isStatic) {
                return null;
            }
            else {
                // Avoid any proxy handling that might be wrapped around the getters and setters
                Field field = getDeclaredField(name);
                return field != null ? field.getType() : metaProperty.getType();
            }
        }
        return null;
    }

    public PropertyDescriptor getPropertyDescriptor(String name) {
        MetaProperty property = theMetaClass.getMetaProperty(name);
        if(property  != null) {
            return createPropertyDescriptor(clazz, property);
        }
        return null;
    }

    /**
     * Creates a PropertyDescriptor from a MetaBeanProperty
     *
     * @param property The bean property
     * @return The descriptor or null
     */
    public static PropertyDescriptor createPropertyDescriptor(Class declaringClass, MetaProperty property) {
        int modifiers = property.getModifiers();
        if(!Modifier.isStatic(modifiers)) {
            String propertyName = property.getName();
            if(property instanceof MetaBeanProperty) {
                MetaBeanProperty beanProperty = (MetaBeanProperty) property;
                MetaMethod getter = beanProperty.getGetter();
                MetaMethod setter = beanProperty.getSetter();
                if(getter instanceof CachedMethod && setter instanceof CachedMethod) {
                    try {
                        Method getterMethod = ((CachedMethod) getter).getCachedMethod();
                        Method setterMethod = ((CachedMethod) setter).getCachedMethod();
                        if(getterMethod.getReturnType().equals(setterMethod.getParameterTypes()[0])) {
                            return new PropertyDescriptor(propertyName, getterMethod, setterMethod);
                        }
                        else {
                            String getterName = NameUtils.getGetterName(propertyName);
                            String setterName = NameUtils.getSetterName(propertyName);
                            getterMethod = declaringClass.getMethod(getterName);
                            setterMethod = declaringClass.getMethod(setterName, getterMethod.getReturnType());
                            return new PropertyDescriptor(propertyName, getterMethod, setterMethod);
                        }
                    } catch (IntrospectionException e) {
                        return null;
                    } catch (NoSuchMethodException e) {
                        return null;
                    }
                }
                else if(getter instanceof CachedMethod) {
                    try {
                        return new PropertyDescriptor(propertyName, ((CachedMethod) getter).getCachedMethod(), null);
                    } catch (IntrospectionException e) {
                        return null;
                    }
                }
            }
            else if(property instanceof MultipleSetterProperty) {
                MultipleSetterProperty msp = (MultipleSetterProperty) property;

                MetaMethod getter = msp.getGetter();
                if(getter instanceof CachedMethod) {
                    try {
                        Method foundGetter = declaringClass.getDeclaredMethod(NameUtils.getGetterName(propertyName));
                        if(foundGetter != null) {

                            Method foundSetter = declaringClass.getDeclaredMethod(NameUtils.getSetterName(propertyName), foundGetter.getReturnType());
                            if(foundSetter != null) {
                                try {
                                    return new PropertyDescriptor(propertyName, foundGetter, foundSetter);
                                } catch (IntrospectionException e) {
                                    return null;
                                }
                            }
                            else {
                                try {
                                    return new PropertyDescriptor(propertyName, foundGetter, null);
                                } catch (IntrospectionException e) {
                                    return null;
                                }
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        // ignore
                    }
                }
            }
        }
        return null;
    }

    public List<PropertyDescriptor> getPropertiesOfType(Class javaClass) {
        List<MetaProperty> properties = theMetaClass.getProperties();
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>(2);

        for (MetaProperty property : properties) {
            int modifiers = property.getModifiers();
            if(Modifier.isStatic(modifiers) || property.getName().contains("$") || !property.getType().equals(javaClass)) continue;

            addBeanProperty(propertyDescriptors, property);
        }
        return propertyDescriptors;
    }

    @SuppressWarnings("unchecked")
    public List<PropertyDescriptor> getPropertiesAssignableToType(Class assignableType) {
        List<MetaProperty> properties = theMetaClass.getProperties();
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>(2);
        for (MetaProperty property : properties) {
            int modifiers = property.getModifiers();
            if(Modifier.isStatic(modifiers) || property.getName().contains("$") || !assignableType.isAssignableFrom(property.getType())) {
                continue;
            }

            addBeanProperty(propertyDescriptors, property);
        }
        return propertyDescriptors;
    }

    @SuppressWarnings("unchecked")
    public List<PropertyDescriptor> getPropertiesAssignableFromType(Class assignableType) {
        List<MetaProperty> properties = theMetaClass.getProperties();
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>(2);
        for (MetaProperty property : properties) {
            int modifiers = property.getModifiers();
            if(Modifier.isStatic(modifiers) || property.getName().contains("$") || !property.getType().isAssignableFrom( assignableType )) continue;
            addBeanProperty(propertyDescriptors, property);
        }
        return propertyDescriptors;
    }

    public static Class<?> getPropertyType(Class<?> cls, String propertyName) {
        MetaProperty metaProperty = GroovySystem.getMetaClassRegistry().getMetaClass(cls).getMetaProperty(propertyName);
        if(metaProperty != null) {
            return metaProperty.getType();
        }
        return null;
    }

    public Field getDeclaredField(String name) {
        MetaProperty metaProperty = theMetaClass.getMetaProperty(name);
        if(metaProperty instanceof MetaBeanProperty) {
            CachedField field = ((MetaBeanProperty) metaProperty).getField();
            if(field != null) {
                return field.getCachedField();
            }
        }
        else if(metaProperty instanceof MultipleSetterProperty) {
            MultipleSetterProperty msp = (MultipleSetterProperty) metaProperty;
            CachedField field = msp.getField();
            if(field != null) {
                return field.getCachedField();
            }
        }
        return null;
    }

    protected void addBeanProperty(List<PropertyDescriptor> propertyDescriptors, MetaProperty property) {
        if(property instanceof MetaBeanProperty) {
            MetaBeanProperty beanProperty = (MetaBeanProperty) property;
            MetaMethod getter = beanProperty.getGetter();
            MetaMethod setter = beanProperty.getSetter();
            boolean isGetterCachedMethod = getter instanceof CachedMethod;
            if(isGetterCachedMethod && setter instanceof CachedMethod) {
                CachedMethod cachedGetter = (CachedMethod) getter;
                CachedMethod cachedSetter = (CachedMethod) setter;
                try {
                    propertyDescriptors.add(new PropertyDescriptor(beanProperty.getName(), cachedGetter.getCachedMethod(), cachedSetter.getCachedMethod()));
                } catch (IntrospectionException e) {
                    // ignore
                }
            }
            else if(isGetterCachedMethod) {
                CachedMethod cachedGetter = (CachedMethod) getter;
                try {
                    propertyDescriptors.add(new PropertyDescriptor(beanProperty.getName(), cachedGetter.getCachedMethod(), null));
                } catch (IntrospectionException e) {
                    // ignore
                }

            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T returnOnlyIfInstanceOf(Object value, Class<T> type) {
        if (value != null && (type == Object.class || ReflectionUtils.isAssignableFrom(type, value.getClass()))) {
            return (T)value;
        }
        return null;
    }

    private static Object getInstancePropertyValue(Object instance, String name, MetaClass metaClass) {
        MetaProperty metaProperty = metaClass.getMetaProperty(name);
        if(metaProperty != null && !Modifier.isStatic(metaProperty.getModifiers())) {
            if(metaProperty instanceof MetaBeanProperty) {
                MetaBeanProperty beanProperty = (MetaBeanProperty) metaProperty;
                CachedField field = beanProperty.getField();
                MetaMethod getter = beanProperty.getGetter();
                Object result = getPropertyWithFieldOrGetter(instance, name, field, getter);
                if (result != null) return result;
            }
            else if(metaProperty instanceof MultipleSetterProperty) {
                MultipleSetterProperty msp = (MultipleSetterProperty) metaProperty;
                CachedField field = msp.getField();
                MetaMethod getter = msp.getGetter();
                Object result = getPropertyWithFieldOrGetter(instance, name, field, getter);
                if (result != null) return result;
            }
            else {
                return metaProperty.getProperty(instance);
            }
        }
        return null;
    }

    private static Object getPropertyWithFieldOrGetter(Object instance, String name, CachedField field, MetaMethod getter) {
        if(field != null) {
            return field.getProperty(instance);
        }
        else {
            if(getter instanceof CachedMethod) {
                return getter.invoke(instance, MetaClassHelper.EMPTY_ARRAY);
            }
            else {
                // take the slow path and reflect
                Method method = org.springframework.util.ReflectionUtils.findMethod(instance.getClass(), NameUtils.getGetterName(name));
                if(method != null) {
                    org.springframework.util.ReflectionUtils.makeAccessible(method);
                    return org.springframework.util.ReflectionUtils.invokeMethod(method, instance);
                }
            }
        }
        return null;
    }
}
