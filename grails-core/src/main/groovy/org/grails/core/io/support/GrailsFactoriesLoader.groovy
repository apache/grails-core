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

package org.grails.core.io.support

import groovy.transform.CompileStatic
import org.grails.io.support.FactoriesLoaderSupport
import org.springframework.core.OrderComparator
import org.springframework.util.Assert
import org.springframework.util.ClassUtils

/**
 * General purpose factory loading mechanism for internal use within the framework.
 *
 * <p>Based on {@link org.springframework.core.io.support.SpringFactoriesLoader}, but applies caching and uses a different file name</p>
 *
 * @since 2.4
 * @author Graeme Rocher
 */
@CompileStatic
class GrailsFactoriesLoader extends FactoriesLoaderSupport {


    private static final Object[] NO_ARGUMENTS = [] as Object[]
    
    
    /**
     * Load the factory implementations of the given type from the default location,
     * using the given class loader.
     * <p>The returned factories are ordered in accordance with the {@link org.springframework.core.OrderComparator}.
     * @param factoryClass the interface or abstract class representing the factory
     */
    static <T> List<T> loadFactories(Class<T> factoryClass) {
        (List<T>)loadFactoriesWithArguments(factoryClass, GrailsFactoriesLoader.class.classLoader)
    }

    /**
     * Load the factory implementations of the given type from the default location,
     * using the given class loader.
     * <p>The returned factories are ordered in accordance with the {@link org.springframework.core.OrderComparator}.
     * @param factoryClass the interface or abstract class representing the factory
     * @param classLoader the ClassLoader to use for loading (can be {@code null} to use the default)
     */
    static <T> List<T> loadFactories(Class<T> factoryClass, ClassLoader classLoader) {
        (List<T>)loadFactoriesWithArguments(factoryClass, classLoader, NO_ARGUMENTS)
    }

    static <T> List<T> loadFactoriesWithArguments(Class<T> factoryClass, ClassLoader classLoader, Object[] arguments) {
        boolean hasArguments = !(arguments != null && arguments.length==0)
        List<T> results = new ArrayList<T>() 
        for(Class<? extends T> clazz : loadFactoryClasses(factoryClass, classLoader)) {
            results.add(hasArguments ? clazz.newInstance(arguments) : clazz.getDeclaredConstructor().newInstance())
        }
        OrderComparator.sort((List<?>) results)
        results
    }
    
    static <T> List<Class<T>> loadFactoryClasses(Class<T> factoryClass, ClassLoader classLoader = GrailsFactoriesLoader.class.classLoader) {
        Assert.notNull factoryClass, "'factoryClass' must not be null"
        
        def factoryNames = loadFactoryNames(factoryClass, classLoader)

        List<Class<T>> result = []
        for (String factoryName in factoryNames) {
            def clazz = loadFactoryClass(factoryName, factoryClass, classLoader)
            if(clazz) {
                result.add clazz
            }
        }
        return result
    }


    private static <T> Class<? extends T> loadFactoryClass(String instanceClassName, Class<T> factoryClass, ClassLoader classLoader) {
        try {
            def instanceClass = ClassUtils.forName(instanceClassName, classLoader)
            if (!factoryClass.isAssignableFrom(instanceClass)) {
                throw new IllegalArgumentException(
                    "Class [$instanceClassName] is not assignable to [$factoryClass.name]")
            }
            return (Class<? extends T>) instanceClass
        }
        catch (Throwable ex) {
            // not creatable in current context, ignore
            return null
        }
    }

    static <T> T loadFactory(Class<T> factoryClass, ClassLoader classLoader = GrailsFactoriesLoader.class.classLoader) {
        def all = loadFactories(factoryClass, classLoader)
        if(all) {
            return all.get(0)
        }
    }

    static <T> T loadFactory(Class<T> factoryClass, Object... arguments) {
        loadFactory(factoryClass, GrailsFactoriesLoader.class.classLoader, arguments)
    }

    static <T> T loadFactory(Class<T> factoryClass, ClassLoader classLoader, Object... arguments) {
        def all = loadFactoriesWithArguments(factoryClass, classLoader, arguments)
        if(all) {
            return (T)all.get(0)
        }
    }
}
