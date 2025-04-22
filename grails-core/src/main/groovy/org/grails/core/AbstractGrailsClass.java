/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.core;

import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.plugins.GrailsVersionUtils;
import grails.util.GrailsMetaClassUtils;
import grails.util.GrailsNameUtils;
import grails.web.Action;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClass;
import groovy.lang.MetaProperty;
import org.grails.core.exceptions.NewInstanceCreationException;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.springframework.beans.BeanUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Abstract base class for Grails types that provides common functionality for
 * evaluating conventions within classes.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 *
 * @since 0.1
 */
public abstract class AbstractGrailsClass implements GrailsClass {

    private final Class<?> clazz;
    private final String fullName;
    private final String name;
    private final String packageName;
    private final String naturalName;
    private final String shortName;
    private final String propertyName;
    private final String logicalPropertyName;
    private ClassPropertyFetcher classPropertyFetcher;
    protected GrailsApplication grailsApplication;
    private boolean isAbstract;

    /**
     * Used by all child classes to create a new instance and get the name right.
     *
     * @param clazz the Grails class
     * @param trailingName the trailing part of the name for this class type
     */
    public AbstractGrailsClass(Class<?> clazz, String trailingName) {
        Assert.notNull(clazz, "Clazz parameter should not be null");

        this.clazz = clazz;
        fullName = clazz.getName();
        packageName = ClassUtils.getPackageName(clazz);
        naturalName = GrailsNameUtils.getNaturalName(clazz.getName());
        shortName = ClassUtils.getShortName(clazz);
        name = GrailsNameUtils.getLogicalName(clazz, trailingName);
        propertyName = GrailsNameUtils.getPropertyNameRepresentation(shortName);
        if (!StringUtils.hasText(name)) {
            logicalPropertyName = propertyName;
        }
        else {
            logicalPropertyName = GrailsNameUtils.getPropertyNameRepresentation(name);
        }
        isAbstract = Modifier.isAbstract(clazz.getModifiers());
    }

    @Override
    public String getPluginName() {
        return GrailsVersionUtils.getPluginName(this.clazz);
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication;
    }

    @Override
    public GrailsApplication getApplication() {
        return this.grailsApplication;
    }

    public String getShortName() {
        return shortName;
    }

    public Class<?> getClazz() {
        return clazz;
    }

    public Object newInstance() {
        try {
            Constructor<?> defaultConstructor = getClazz().getDeclaredConstructor(new Class[]{});
            if (!defaultConstructor.isAccessible()) {
                defaultConstructor.setAccessible(true);
            }
            return defaultConstructor.newInstance(new Object[]{});
        }
        catch (Exception e) {
            Throwable targetException;
            if (e instanceof InvocationTargetException) {
                targetException = ((InvocationTargetException)e).getTargetException();
            }
            else {
                targetException = e;
            }
            throw new NewInstanceCreationException("Could not create a new instance of class [" +
                    getClazz().getName() + "]!", targetException);
        }
    }

    public String getName() {
        return name;
    }

    public String getNaturalName() {
        return naturalName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPropertyName() {
        return propertyName;
    }

    public String getLogicalPropertyName() {
        return logicalPropertyName;
    }

    public String getPackageName() {
        return packageName;
    }

    public Object getReferenceInstance() {
        Object obj = BeanUtils.instantiateClass(clazz);
        if (obj instanceof GroovyObject) {
            ((GroovyObject)obj).setMetaClass(getMetaClass());
        }
        return obj;
    }


    /**
     * @deprecated Use {@link #getMetaProperties()} instead
     */
    @Deprecated
    public PropertyDescriptor[] getPropertyDescriptors() {
        return resolvePropertyFetcher().getPropertyDescriptors();
    }

    private ClassPropertyFetcher resolvePropertyFetcher() {
        if(classPropertyFetcher == null) {
            classPropertyFetcher = ClassPropertyFetcher.forClass(clazz);
        }
        return classPropertyFetcher;
    }

    public List<MetaProperty> getMetaProperties() {
        return resolvePropertyFetcher().getMetaProperties();
    }

    public Class<?> getPropertyType(String typeName) {
        return ClassPropertyFetcher.getPropertyType(getClazz(), typeName);
    }

    public boolean isReadableProperty(String propName) {
        return ClassPropertyFetcher.getPropertyType(getClazz(), propName) != null;
    }

    public boolean isActionMethod(String methodName) {
        Method m =  ReflectionUtils.findMethod(getClazz(), methodName, new Class[0]);
        if (m != null) {
            ReflectionUtils.makeAccessible(m);
        }
        return m != null && m.getAnnotation(Action.class) != null;
    }

    public boolean hasMetaMethod(String methodName) {
        return hasMetaMethod(methodName, null);
    }

    public boolean hasMetaMethod(String methodName, Object[] args) {
        return (getMetaClass().getMetaMethod(methodName, args) != null);
    }

    public boolean hasMetaProperty(String propName) {
        return (getMetaClass().getMetaProperty(propName) != null);
    }


    /**
     * <p>Looks for a property of the reference instance with a given name and type.</p>
     * <p>If found its value is returned. We follow the Java bean conventions with augmentation for groovy support
     * and static fields/properties. We will therefore match, in this order:
     * </p>
     * <ol>
     * <li>Public static field
     * <li>Public static property with getter method
     * <li>Standard public bean property (with getter or just public field, using normal introspection)
     * </ol>
     *
     * @return property value or null if no property or static field was found
     */
    protected <T> T getPropertyOrStaticPropertyOrFieldValue(String name, Class<T> type) {
        return ClassPropertyFetcher.getStaticPropertyValue(getClazz(), name, type);
    }

    /**
     * Get the value of the named static property.
     *
     * @param propName
     * @param type
     * @return The property value or null
     */
    public <T> T getStaticPropertyValue(String propName, Class<T> type) {
        return ClassPropertyFetcher.getStaticPropertyValue(getClazz(), propName, type);
    }

    /**
     * Get the value of the named property, with support for static properties in both Java and Groovy classes
     * (which as of Groovy JSR 1.0 RC 01 only have getters in the metaClass)
     * @param propName
     * @param type
     * @return The property value or null
     */
    public <T> T getPropertyValue(String propName, Class<T> type) {
        return ClassPropertyFetcher.getStaticPropertyValue(getClazz(), propName, type);
    }


    public Object getPropertyValueObject(String propertyNAme) {
        return getPropertyValue(propertyNAme, Object.class);
    }


    /* (non-Javadoc)
     * @see grails.core.GrailsClass#getPropertyValue(java.lang.String)
     */
    public Object getPropertyValue(String propName) {
        return getPropertyOrStaticPropertyOrFieldValue(propName, Object.class);
    }

    public boolean isAbstract() {

        return isAbstract;
    }

    /* (non-Javadoc)
    * @see grails.core.GrailsClass#hasProperty(java.lang.String)
    */
    public boolean hasProperty(String propName) {
        return ClassPropertyFetcher.getPropertyType(getClazz(), propName) != null;
    }

    /**
     * @return the metaClass
     */
    public MetaClass getMetaClass() {
        return GrailsMetaClassUtils.getExpandoMetaClass(getClazz());
    }

    @Override
    public String toString() {
        return "Artefact > " + getName();
    }
}
