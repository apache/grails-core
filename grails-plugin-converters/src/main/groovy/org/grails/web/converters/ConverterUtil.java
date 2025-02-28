/*
 * Copyright 2006-2007 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters;

import groovy.lang.Closure;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.util.Collection;
import java.util.Map;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.NullObject;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;
import org.grails.web.converters.exceptions.ConverterException;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

/**
 * A utility class for creating and dealing with Converter objects.
 *
 * @author Siegfried Puchbauer
 * @since 0.6
 */
public class ConverterUtil {

    private static final String PERSISTENCE_BEAN_WRAPPER_CLASS = "org.codehaus.groovy.grails.orm.hibernate.support.HibernateBeanWrapper";

    private static final Object[] EMPTY_OBJECT_ARRAY = {};

    private ConverterUtil() {
       // static only
    }

    public static BeanWrapper createBeanWrapper(Object o) {
        BeanWrapper beanWrapper;
        try {
            Class<?> c = Class.forName(PERSISTENCE_BEAN_WRAPPER_CLASS, true, Thread.currentThread().getContextClassLoader());
            Constructor<?> init = c.getConstructor(new Class[]{Object.class});
            beanWrapper = (BeanWrapper)init.newInstance(new Object[]{o});
        }
        catch (Exception e) {
            beanWrapper = new BeanWrapperImpl(o);
        }
        return beanWrapper;
    }

    public static Object createConverter(Class<?> converterClass, Object target) throws ConverterException {
        return createConverter(converterClass, target, null);
    }

    public static <T> T createConverter(Class<T> converterClass, Object target, ApplicationContext applicationContext) throws ConverterException{
        try {
            T converter = converterClass.newInstance();
            if (converter instanceof ApplicationContextAware && applicationContext != null) {
                ((ApplicationContextAware)converter).setApplicationContext(applicationContext);
            }
            ((AbstractConverter)converter).setTarget(target);
            return converter;
        }
        catch (Exception e) {
            throw new ConverterException("Initialization of Converter Object " + converterClass.getName() +
                    " failed for target " + target.getClass().getName(), e);
        }
    }

    public static String trimProxySuffix(String name) {
        int i = name.indexOf("$$");
        if (i > -1) {
            name = name.substring(0, i);
            while (name.endsWith("_")) {
                name = name.substring(0, name.length() - 1);
            }
        }
        return name;
    }

    public static boolean isConverterClass(Class<?> clazz) {
        return Converter.class.isAssignableFrom(clazz);
    }

    @SuppressWarnings("rawtypes")
    public static Object invokeOriginalAsTypeMethod(Object delegate, Class<?> clazz) {
        if (clazz.isInstance(delegate)) {
            return delegate;
        }

        if (delegate instanceof NullObject) {
            return ((NullObject)delegate).asType(clazz);
        }
        if (delegate instanceof Collection<?> && clazz.isArray()) {
            int size = ((Collection<?>)delegate).size();
            if (clazz.getComponentType() == Object.class) {
                if (size == 0) {
                    return EMPTY_OBJECT_ARRAY;
                }
                return ((Collection<?>)delegate).toArray((Object[])Array.newInstance(clazz.getComponentType(), size));
            }
            if (size == 0) {
                return Array.newInstance(clazz.getComponentType(), 0);
            }
            return DefaultTypeTransformation.asArray(delegate, clazz);
        }

        if (delegate instanceof Collection<?>) {
            return DefaultGroovyMethods.asType((Collection<?>) delegate, clazz);
        }

        if (delegate instanceof Closure) {
            return DefaultGroovyMethods.asType((Closure) delegate, clazz);
        }

        if (delegate instanceof Map) {
            return DefaultGroovyMethods.asType((Map) delegate, clazz);
        }

        if (delegate instanceof Number) {
            return DefaultGroovyMethods.asType((Number) delegate, clazz);
        }

        if (delegate instanceof File) {
            return ResourceGroovyMethods.asType((File) delegate, clazz);
        }

        if (delegate instanceof String) {
            return StringGroovyMethods.asType((String) delegate, clazz);
        }

        return DefaultGroovyMethods.asType(delegate, clazz);
    }

    public static ConverterException resolveConverterException(Throwable t) {
        return t instanceof ConverterException ? (ConverterException) t : new ConverterException(t);
    }

    public static Converter.CircularReferenceBehaviour resolveCircularReferenceBehaviour(String str) {
        return Converter.CircularReferenceBehaviour.valueOf(str);
    }
}
