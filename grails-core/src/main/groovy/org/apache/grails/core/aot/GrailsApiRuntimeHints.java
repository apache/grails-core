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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.ClassUtils;

import grails.config.Config;
import grails.config.ConfigMap;
import grails.config.Settings;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.Plugin;
import grails.spring.BeanBuilder;
import grails.util.BuildSettings;
import grails.util.GrailsMessageSourceUtils;
import grails.util.GrailsUtil;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.spring.RuntimeSpringConfiguration;
import org.grails.validation.ConstraintEvalUtils;

/**
 * Registers the interfaces a plugin reaches through dynamically: the framework's own, and the
 * Spring ones it is handed.
 *
 * <p>A plugin descriptor is Groovy, and much of it is written without static compilation -- so
 * reading a configuration value, asking the application for its artefacts, or asking the plugin
 * manager about a plugin are all calls resolved where they are written rather than when the
 * descriptor is compiled. Each is therefore made reflectively.</p>
 *
 * <p>An image keeps a method for that only when something has asked it to, and an application never
 * names these itself: they are the framework's, called from the framework's own descriptors. The
 * image refuses the call where it is made, and what it reports is a method of an interface that
 * appears nowhere in the application -- the last of them stopping a context from starting on
 * {@code ConfigMap.getProperty}, which is how nearly every plugin reads its settings.</p>
 *
 * <p>The Spring interfaces are here for the same reason rather than a different one: a descriptor is
 * given the environment and the context and calls them the same dynamic way, and an application does
 * not name them either. Reading a setting from the environment stopped a Hibernate application from
 * starting in exactly the way reading one from the configuration stopped every application.</p>
 *
 * <p>The logger is here for the same reason again. A descriptor written without static compilation
 * that declares {@code @Slf4j} creates its logger in its static initializer by a dynamic call to
 * {@code LoggerFactory.getLogger}, and logs through it the same way, so the first such descriptor to
 * load stopped the application with "No signature of static method: getLogger" before any plugin had
 * been registered.</p>
 *
 * <p>Only the interfaces are named. What implements them is reached through them, and registering
 * the implementations would be registering most of the framework.</p>
 *
 * <p>The exception is the framework's own classes of constants and static helpers, which have no interface
 * to be reached through: a descriptor or a closure written without static compilation reads
 * {@code Settings.MIME_TYPES} or asks {@code Environment.current} the same reflective way, so their
 * constants and methods are registered as well.</p>
 *
 * @since 8.0
 */
public class GrailsApiRuntimeHints implements RuntimeHintsRegistrar {

    /** What a descriptor written without static compilation calls on the framework. */
    private static final Class<?>[] TYPES = {
        Config.class,
        ConfigMap.class,
        GrailsApplication.class,
        GrailsClass.class,
        GrailsPlugin.class,
        GrailsPluginManager.class,
        // What a descriptor is handed by Spring and calls the same way
        Environment.class,
        ConfigurableEnvironment.class,
        PropertyResolver.class,
        ApplicationContext.class,
        ConfigurableApplicationContext.class,
        ResourceLoader.class,
        // What the bean DSL hands a descriptor's closures, which ask it what is already declared
        BeanFactory.class,
        // How a descriptor's logger is created, and what it logs through
        LoggerFactory.class,
        Logger.class
    };

    /**
     * The framework's classes of constants and static helpers that code written without static compilation
     * reads and calls, and the base class and builders a descriptor calls into.
     */
    private static final Class<?>[] CLASSES = {
        Settings.class,
        BuildSettings.class,
        grails.util.Environment.class,
        GrailsUtil.class,
        GrailsMessageSourceUtils.class,
        GrailsFactoriesLoader.class,
        ConstraintEvalUtils.class,
        Plugin.class,
        BeanBuilder.class,
        RuntimeSpringConfiguration.class,
        // What the bean DSL sets a bean's options on, and the helper a descriptor checks for optional
        // dependencies with
        AbstractBeanDefinition.class,
        ClassUtils.class
    };

    /**
     * The same, for classes this one cannot name: where a request's attributes and views are found, which is in
     * a module this one does not depend on; and the property {@code metaClass.encodeAsHTML << { ... }} adds a
     * method through, which Groovy declares {@code protected} and its own metadata does not cover -- the codecs
     * add every {@code encodeAs} method that way.
     */
    private static final String[] CLASS_NAMES = {
        "org.grails.web.util.GrailsApplicationAttributes",
        "groovy.lang.ExpandoMetaClass$ExpandoMetaProperty"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : TYPES) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_METHODS);
        }
        for (Class<?> type : CLASSES) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS);
        }
        for (String type : CLASS_NAMES) {
            hints.reflection().registerTypeIfPresent(classLoader, type, MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.ACCESS_PUBLIC_FIELDS);
        }
    }
}
