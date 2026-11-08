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

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;
import org.springframework.core.io.ResourceLoader;

import grails.config.Config;
import grails.config.ConfigMap;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;

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
 * <p>Only the interfaces are named. What implements them is reached through them, and registering
 * the implementations would be registering most of the framework.</p>
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
        ResourceLoader.class
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : TYPES) {
            hints.reflection().registerType(type, MemberCategory.INVOKE_DECLARED_METHODS);
        }
    }
}
