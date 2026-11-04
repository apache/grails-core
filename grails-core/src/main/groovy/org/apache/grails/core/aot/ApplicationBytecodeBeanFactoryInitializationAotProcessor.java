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
package org.apache.grails.core.aot;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.ClassUtils;

import grails.boot.config.GrailsAutoConfiguration;

/**
 * Registers the reflection the application's own classes imply, read from their bytecode the way
 * {@link BytecodeRuntimeHints} reads the framework's: creating a Groovy enum's constants, initializing a
 * trait from the classes implementing it, turning a closure into the interface a method takes, asking a
 * Groovy class about its methods, and the members Spring injects or calls through an annotation.
 *
 * <p>{@link BytecodeRuntimeHints} covers the framework's packages. An application's classes are in
 * its own, which are only known once the application is: they are the packages its application class
 * names, the same ones the framework scans for its artefacts. Without this, an application declaring a
 * Groovy enum -- a status on a domain class, say -- could not use it in an image, because the enum could not
 * create its constants.</p>
 *
 * @since 9.0
 */
public class ApplicationBytecodeBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {

    private static final Log logger = LogFactory.getLog(ApplicationBytecodeBeanFactoryInitializationAotProcessor.class);

    @Override
    @Nullable
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = applicationPackages(beanFactory);
        if (packages.isEmpty()) {
            return null;
        }
        String[] patterns = packages.stream()
                .map(name -> ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                        ClassUtils.convertClassNameToResourcePath(name) + "/**/*.class")
                .toArray(String[]::new);
        ClassLoader classLoader = beanFactory.getBeanClassLoader();
        return (generationContext, beanFactoryInitializationCode) ->
                BytecodeRuntimeHints.registerFoundIn(generationContext.getRuntimeHints(), classLoader, patterns);
    }

    /**
     * The packages the application's classes are in, as its application class names them, or none if this is
     * not a Grails application context.
     */
    private Set<String> applicationPackages(ConfigurableListableBeanFactory beanFactory) {
        Set<String> packages = new LinkedHashSet<>();
        for (String name : beanFactory.getBeanDefinitionNames()) {
            String className = beanFactory.getBeanDefinition(name).getBeanClassName();
            if (className == null) {
                continue;
            }
            Class<?> beanClass;
            try {
                beanClass = ClassUtils.forName(className, beanFactory.getBeanClassLoader());
            }
            catch (ClassNotFoundException | LinkageError ex) {
                continue;
            }
            if (GrailsAutoConfiguration.class.isAssignableFrom(beanClass) && beanClass != GrailsAutoConfiguration.class) {
                packages.addAll(packagesNamedBy(beanClass));
            }
        }
        return packages;
    }

    /**
     * The packages an application class names. An instance is created only to ask, as the framework does
     * when it scans for the application's artefacts, because the list is a method the compiler writes into
     * the class.
     */
    private Collection<String> packagesNamedBy(Class<?> applicationClass) {
        try {
            return ((GrailsAutoConfiguration) applicationClass.getDeclaredConstructor().newInstance()).packageNames();
        }
        catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
            logger.debug("Unable to ask " + applicationClass.getName() + " for its packages", ex);
            String packageName = applicationClass.getPackageName();
            return packageName.isEmpty() ? Set.of() : Set.of(packageName);
        }
    }

}
