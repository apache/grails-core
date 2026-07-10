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

/**
 * Descriptor for a future build-time URL mappings index.
 *
 * @since 8.1
 */
public final class UrlMappingsIndexProperties {

    public static final String LOCATION = "META-INF/grails/url-mappings-index.properties";

    private static final UrlMappingsIndexProperties EMPTY = new UrlMappingsIndexProperties(false, new Properties());

    private final boolean present;
    private final Properties properties;

    private UrlMappingsIndexProperties(boolean present, Properties properties) {
        this.present = present;
        this.properties = properties;
    }

    public static UrlMappingsIndexProperties load(ClassLoader classLoader) {
        ClassLoader loader = classLoader == null ? Thread.currentThread().getContextClassLoader() : classLoader;
        if (loader == null) {
            return EMPTY;
        }
        try (InputStream inputStream = loader.getResourceAsStream(LOCATION)) {
            if (inputStream == null) {
                return EMPTY;
            }
            Properties properties = new Properties();
            properties.load(inputStream);
            return new UrlMappingsIndexProperties(true, properties);
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to load " + LOCATION, e);
        }
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
