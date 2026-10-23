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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import org.apache.grails.common.aot.RegistrableTypes;

/**
 * Registers the classes named in {@code META-INF/grails.factories}, which the framework creates by name.
 *
 * <p>The artefact handlers, and every other factory a module declares there, are found by reading the
 * file and created reflectively through their constructors. An image keeps a constructor only when
 * something asked for it, so an image built without these failed while the application was being
 * initialized, with {@code NoSuchMethodException} naming the first artefact handler's constructor.</p>
 *
 * <p>The files are read while the hints are written, on an ordinary JVM with the full classpath, so every
 * module's declarations are covered, including a plugin's from outside the framework. A class that cannot
 * be loaded here is skipped, because registering it would fail the image build.</p>
 *
 * @since 9.0
 */
public class GrailsFactoriesRuntimeHints implements RuntimeHintsRegistrar {

    private static final Log logger = LogFactory.getLog(GrailsFactoriesRuntimeHints.class);

    /**
     * Where the factories are declared. Written out rather than taken from {@code FactoriesLoaderSupport},
     * whose constant is a Groovy property and so not visible here.
     */
    private static final String FACTORIES = "META-INF/grails.factories";

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        hints.resources().registerPattern(FACTORIES);
        ClassLoader loader = (classLoader != null) ? classLoader : ClassUtils.getDefaultClassLoader();
        for (String factory : declaredFactories(loader)) {
            if (RegistrableTypes.loads(factory, loader)) {
                hints.reflection().registerTypeIfPresent(loader, factory, MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            }
        }
    }

    /** Every class any {@code grails.factories} on the classpath names, whatever it is declared as. */
    private static Set<String> declaredFactories(@Nullable ClassLoader loader) {
        Set<String> factories = new LinkedHashSet<>();
        Enumeration<URL> resources;
        try {
            resources = (loader != null) ? loader.getResources(FACTORIES) : ClassLoader.getSystemResources(FACTORIES);
        }
        catch (IOException ex) {
            logger.warn("Unable to find " + FACTORIES + " on the classpath", ex);
            return factories;
        }
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            Properties properties = new Properties();
            try (InputStream input = resource.openStream()) {
                properties.load(input);
            }
            catch (IOException | IllegalArgumentException ex) {
                logger.warn("Unable to read " + resource, ex);
                continue;
            }
            for (String factoryType : properties.stringPropertyNames()) {
                for (String name : StringUtils.commaDelimitedListToStringArray(properties.getProperty(factoryType))) {
                    String trimmed = name.trim();
                    if (!trimmed.isEmpty()) {
                        factories.add(trimmed);
                    }
                }
            }
        }
        return factories;
    }

}
