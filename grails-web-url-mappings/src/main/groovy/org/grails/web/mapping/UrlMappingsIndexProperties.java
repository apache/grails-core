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
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Optional descriptor for a future build-time URL mappings index.
 *
 * <p>Nothing consumes this yet. Callers that need a classloader should resolve one at the
 * call site (for example {@code ClassUtils.getDefaultClassLoader()}) and pass it here.
 * Every matching resource on that loader is tried in classpath order; the first readable
 * copy wins, and a malformed or unreadable copy does not abort discovery of later ones.</p>
 *
 * @since 8.1
 */
final class UrlMappingsIndexProperties {

    static final String LOCATION = "META-INF/grails/url-mappings-index.properties";

    private static final Log LOG = LogFactory.getLog(UrlMappingsIndexProperties.class);
    private static final UrlMappingsIndexProperties EMPTY = new UrlMappingsIndexProperties(false, new Properties());

    private final boolean present;
    private final Properties properties;

    private UrlMappingsIndexProperties(boolean present, Properties properties) {
        this.present = present;
        this.properties = properties;
    }

    static UrlMappingsIndexProperties load(ClassLoader classLoader) {
        if (classLoader == null) {
            return EMPTY;
        }
        Enumeration<URL> resources;
        try {
            resources = classLoader.getResources(LOCATION);
        }
        catch (IOException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to enumerate " + LOCATION + " from " + classLoader + "; ignoring descriptor", e);
            }
            return EMPTY;
        }
        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            try (InputStream inputStream = resource.openStream()) {
                Properties loaded = new Properties();
                loaded.load(inputStream);
                return new UrlMappingsIndexProperties(true, loaded);
            }
            catch (IOException | IllegalArgumentException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to load " + LOCATION + " from " + resource + "; trying next resource", e);
                }
            }
        }
        return EMPTY;
    }

    boolean isPresent() {
        return present;
    }

    Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    String getProperty(String name) {
        return properties.getProperty(name);
    }

    Iterable<String> propertyNames() {
        return present ? properties.stringPropertyNames() : Collections.emptySet();
    }
}
