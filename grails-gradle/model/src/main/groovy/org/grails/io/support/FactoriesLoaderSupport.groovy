
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
package org.grails.io.support

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import groovy.transform.CompileStatic

/**
 * Base functionality for loading grails.factories
 *
 * @since 3.0
 */
@CompileStatic
class FactoriesLoaderSupport {

    /** The location to look for the factories. Can be present in multiple JAR files. */
    static final String FACTORIES_RESOURCE_LOCATION = 'META-INF/grails.factories'

    /** The location to look for CLI command registrations. Can be present in multiple JAR files. */
    static final String CLI_FACTORIES_RESOURCE_LOCATION = 'META-INF/grails-cli.factories'

    private static ConcurrentMap<String, Map<String,String[]>> loadedPropertiesForClassLoader = new ConcurrentHashMap<String, Map<String,String[]>>()

    /**
     * Loads the names of the classes from grails.factories without loading the classes themselves
     *
     * @param factoryClass The factory class
     * @param classLoader The ClassLoader
     *
     * @return An array of classes that implement the factory class
     */
    static String[] loadFactoryNames(Class<?> factoryClass, ClassLoader classLoader = FactoriesLoaderSupport.classLoader,
                                     String resourceLocation = FACTORIES_RESOURCE_LOCATION) {
        String factoryClassName = factoryClass.getName()
        return loadFactoryNames(factoryClassName, classLoader, resourceLocation)
    }

    /**
     * Loads the names of the classes from grails.factories without loading the classes themselves
     *
     * @param factoryClass The factory class
     * @param classLoader The ClassLoader
     *
     * @return An array of classes that implement the factory class
     */
    static String[] loadFactoryNames(String factoryClassName, ClassLoader classLoader = FactoriesLoaderSupport.classLoader,
                                     String resourceLocation = FACTORIES_RESOURCE_LOCATION) {
        try {
            String cacheKey = "${System.identityHashCode(classLoader)}:${resourceLocation}"
            Map<String, String[]> loadedProperties = loadedPropertiesForClassLoader.get(cacheKey)
            if (loadedProperties == null) {
                Set<String> allKeys = [] as Set
                def urls = classLoader.getResources(resourceLocation)
                Collection<Properties> allProperties = []
                urls.each { URL url ->
                    def properties = new Properties()
                    url.withInputStream { InputStream input ->
                        properties.load(input)
                    }
                    allProperties.add(properties)
                    allKeys.addAll((Set<String>) properties.keySet())
                }
                Map<String, String[]> mergedFactoryNames = [:]
                for (String propertyName : allKeys) {
                    Set<String> result = [] as Set
                    for (Properties props : allProperties) {
                        String factoryClassNames = props.getProperty(propertyName)
                        if (factoryClassNames) {
                            result.addAll(factoryClassNames.split(',').toList())
                        }
                    }
                    mergedFactoryNames.put(propertyName, result as String[])
                }
                loadedProperties = loadedPropertiesForClassLoader.putIfAbsent(cacheKey, mergedFactoryNames)
                if (loadedProperties == null) {
                    loadedProperties = mergedFactoryNames
                }
            }
            return loadedProperties.get(factoryClassName)
        }
        catch (IOException ex) {
            throw new IllegalArgumentException("Unable to load [$factoryClassName] factories from location [$resourceLocation]", ex)
        }
    }
}
