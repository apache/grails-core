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
package org.grails.plugins.web.controllers;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

import grails.config.Settings;

/**
 * Translates the deprecated {@code grails.filter.encoding} and {@code grails.filter.forceEncoding} into
 * {@code spring.servlet.encoding.charset} and {@code spring.servlet.encoding.force}, which configure the
 * character encoding filter Spring Boot registers. Grails no longer registers an encoding filter of its own.
 *
 * <p>It is a context initializer, not an environment post-processor, because files named by
 * {@code grails.config.locations} are loaded by {@code ExternalConfigRunListener} after the post-processors run.
 * It translates from a bean factory post-processor that it adds to the context, because the property source of a
 * {@code @PropertySource} annotation is only added while the context processes its configuration classes. That
 * post-processor runs after both, and before any bean binds {@code spring.servlet.encoding}.
 * A translated value is contributed as a Spring Boot default property (lowest precedence), and only when the
 * application does not set the Spring Boot property itself.
 *
 * @since 9.0
 * @deprecated only needed while applications move to {@code spring.servlet.encoding}; it will be removed
 * together with the two properties it translates
 */
@Deprecated(since = "9.0", forRemoval = true)
public class GrailsCharacterEncodingInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(GrailsCharacterEncodingInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        applicationContext.addBeanFactoryPostProcessor(beanFactory -> translate(applicationContext.getEnvironment()));
    }

    @SuppressWarnings("removal")
    private void translate(ConfigurableEnvironment environment) {
        var translated = new LinkedHashMap<String, Object>();
        translate(environment, Settings.FILTER_ENCODING, "spring.servlet.encoding.charset", translated);
        translate(environment, Settings.FILTER_FORCE_ENCODING, "spring.servlet.encoding.force", translated);
        if (!translated.isEmpty()) {
            DefaultPropertiesPropertySource.addOrMerge(translated, environment.getPropertySources());
        }
    }

    private void translate(ConfigurableEnvironment environment, String deprecatedProperty,
            String replacement, Map<String, Object> translated) {
        var value = environment.getProperty(deprecatedProperty);
        if (value == null) {
            return;
        }
        // DefaultPropertiesPropertySource.addOrMerge replaces a key, and the application may have set this one as a
        // default property itself
        if (environment.containsProperty(replacement)) {
            logger.warn("'{}' is deprecated and will be removed. It is ignored, because '{}' is set.",
                    deprecatedProperty, replacement);
            return;
        }
        logger.warn("'{}' is deprecated and will be removed. Spring Boot registers the character encoding filter " +
                "now, so use '{}={}' instead.", deprecatedProperty, replacement, value);
        translated.put(replacement, value);
    }
}
