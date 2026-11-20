/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.plugins.i18n;

import java.util.List;

import org.springframework.aot.hint.ResourceHints;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * Registers the native-image resource hints the message bundles need.
 *
 * <p>Spring Boot's own {@code MessageSourceRuntimeHints} registers two hardcoded patterns,
 * {@code messages.properties} and {@code messages_*.properties} — it does not derive anything from the
 * configured base names. Every Grails plugin bundle uses a namespaced base name, so without this all
 * plugin messages would resolve on the JVM and silently vanish in a native image.</p>
 *
 * <p>Hints are derived from the <em>effective</em> {@code spring.messages.basename} rather than from
 * the descriptors directly, so a base name the application configured by hand — pointing at a bundle
 * outside {@code grails-app/i18n} — is covered too, instead of leaving the application to maintain
 * native metadata itself. Reading the environment is why this is a
 * {@link BeanFactoryInitializationAotProcessor} rather than a plain
 * {@code RuntimeHintsRegistrar}, which has no access to it.</p>
 *
 * @since 8.0
 */
public class I18nRuntimeHintsProcessor implements BeanFactoryInitializationAotProcessor {

    private static final String BASENAME_PROPERTY = "spring.messages.basename";

    private static final String DEFAULT_BASENAME = "messages";

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Override
    public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
        List<String> basenames = basenames(beanFactory);
        if (basenames.isEmpty()) {
            return null;
        }
        return (generationContext, beanFactoryInitializationCode) -> {
            ResourceHints resources = generationContext.getRuntimeHints().resources();
            // The descriptors are read at runtime through an exact-name ClassLoader.getResources
            // lookup, so the descriptor itself has to be present in the image.
            resources.registerPattern(I18nDescriptors.DESCRIPTOR_PATH);
            for (String basename : basenames) {
                String path = toResourcePath(basename);
                resources.registerPattern(path + ".properties");
                resources.registerPattern(path + "_*.properties");
            }
        };
    }

    private List<String> basenames(ConfigurableListableBeanFactory beanFactory) {
        Environment environment = environment(beanFactory);
        if (environment == null) {
            return List.of();
        }
        return Binder.get(environment).bind(BASENAME_PROPERTY, Bindable.listOf(String.class))
                .orElseGet(() -> List.of(DEFAULT_BASENAME));
    }

    private Environment environment(ConfigurableListableBeanFactory beanFactory) {
        if (!beanFactory.containsBean(ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME)) {
            return null;
        }
        Object environment = beanFactory.getBean(ConfigurableApplicationContext.ENVIRONMENT_BEAN_NAME);
        return (environment instanceof Environment resolved) ? resolved : null;
    }

    /**
     * Converts a base name to the resource path it resolves to.
     *
     * <p>Base names are {@link java.util.ResourceBundle} names, not file paths:
     * {@code config.i18n.custom} resolves to {@code config/i18n/custom.properties}, not
     * {@code config.i18n.custom.properties}. Spring Boot applies the same mapping when it checks
     * whether a bundle exists, and {@code MessageSourceProperties} documents the contract as the
     * resource-bundle convention "with relaxed support for slash based locations", so a slash form is
     * already a path and passes through unchanged.</p>
     *
     * @throws IllegalArgumentException for a {@code classpath:}-prefixed base name, which
     * {@code ResourceBundleMessageSource} cannot resolve, so silently generating a broken pattern
     * would hide the mistake until it failed in a native image
     */
    private static String toResourcePath(String basename) {
        String trimmed = basename.trim();
        if (trimmed.startsWith(CLASSPATH_PREFIX)) {
            throw new IllegalArgumentException("Invalid " + BASENAME_PROPERTY + " entry '" + trimmed +
                    "'. Base names are ResourceBundle names resolved from the classpath root, so the '" +
                    CLASSPATH_PREFIX + "' prefix is not supported; use '" +
                    trimmed.substring(CLASSPATH_PREFIX.length()) + "' instead.");
        }
        return trimmed.replace('.', '/');
    }
}
