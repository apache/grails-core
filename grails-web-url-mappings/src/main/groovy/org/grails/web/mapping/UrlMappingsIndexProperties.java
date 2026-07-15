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
package org.grails.web.mapping;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Descriptor for a future build-time URL mappings index.
 *
 * @since 8.0.x
 */
public final class UrlMappingsIndexProperties {

    public static final String LOCATION = "META-INF/grails/url-mappings-index.properties";

    private static final Log LOG = LogFactory.getLog(UrlMappingsIndexProperties.class);
    private static final UrlMappingsIndexProperties EMPTY = new UrlMappingsIndexProperties(false, new Properties());

    private final boolean present;
    private final Properties properties;

    private UrlMappingsIndexProperties(boolean present, Properties properties) {
        this.present = present;
        this.properties = properties;
    }

    public static UrlMappingsIndexProperties load(ClassLoader classLoader) {
        try {
            ClassLoader threadContextClassLoader = Thread.currentThread().getContextClassLoader();
            for (ClassLoader loader : new ClassLoader[] {threadContextClassLoader, classLoader}) {
                if (loader == null) {
                    continue;
                }
                try (InputStream inputStream = loader.getResourceAsStream(LOCATION)) {
                    if (inputStream == null) {
                        continue;
                    }
                    Properties properties = new Properties();
                    properties.load(inputStream);
                    return new UrlMappingsIndexProperties(true, properties);
                }
            }
        }
        catch (IOException | RuntimeException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to load " + LOCATION + "; ignoring descriptor", e);
            }
            return EMPTY;
        }
        return EMPTY;
    }

    public boolean isPresent() {
        return present;
    }

    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    public String getProperty(String name) {
        return properties.getProperty(name);
    }

    public Iterable<String> propertyNames() {
        return present ? properties.stringPropertyNames() : Collections.emptySet();
    }
}
