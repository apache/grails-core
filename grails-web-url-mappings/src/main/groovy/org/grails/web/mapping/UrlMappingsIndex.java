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
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grails.web.mapping.UrlMapping;

/**
 * Every build-time URL mapping index visible to one class loader.
 *
 * <p>The Grails Gradle plugin writes one {@value #LOCATION} into each application and each plugin.
 * Those are separate classpath roots, so a single {@code getResourceAsStream} would keep whichever
 * copy it found first. This reads every copy with {@link ClassLoader#getResources}.</p>
 *
 * <p>A caller that passes a class loader gets that loader and no other. The thread context loader is
 * used only when the caller passes {@code null}. One unreadable index does not drop the rest:
 * {@link Properties#load(Reader)} reports bad input as {@link IOException} or
 * {@link IllegalArgumentException}, and that copy is skipped.</p>
 *
 * @since 8.1
 */
final class UrlMappingsIndex {

    /** Path of the generated index within an application or plugin classpath root. */
    static final String LOCATION = "META-INF/grails/url-mappings.index";

    /** The only index format this release understands. */
    static final String FORMAT_VERSION = "1";

    private static final Logger LOG = LoggerFactory.getLogger(UrlMappingsIndex.class);
    private static final UrlMappingsIndex EMPTY = new UrlMappingsIndex(List.of());

    private final List<Entry> entries;

    private UrlMappingsIndex(List<Entry> entries) {
        this.entries = entries;
    }

    static UrlMappingsIndex empty() {
        return EMPTY;
    }

    /**
     * Loads every index the class loader can see.
     *
     * @param classLoader the loader to search; the thread context loader when {@code null}
     * @return the merged index, never {@code null}
     */
    static UrlMappingsIndex load(ClassLoader classLoader) {
        ClassLoader loader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            return EMPTY;
        }
        Enumeration<URL> urls;
        try {
            urls = loader.getResources(LOCATION);
        }
        catch (IOException | SecurityException ex) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unable to enumerate " + LOCATION + " from " + loader, ex);
            }
            return EMPTY;
        }
        List<Entry> merged = new ArrayList<>();
        int stalled = 0;
        while (stalled < 8) {
            boolean more;
            try {
                more = urls.hasMoreElements();
            }
            catch (SecurityException ex) {
                stalled++;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to enumerate " + LOCATION + " from " + loader, ex);
                }
                continue;
            }
            if (!more) {
                break;
            }
            URL url;
            try {
                url = urls.nextElement();
            }
            catch (SecurityException ex) {
                stalled++;
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to enumerate " + LOCATION + " from " + loader, ex);
                }
                continue;
            }
            stalled = 0;
            try {
                merged.addAll(read(url));
            }
            catch (IOException | IllegalArgumentException | SecurityException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Unable to load " + LOCATION + " from " + url + "; ignoring that index", ex);
                }
            }
        }
        return merged.isEmpty() ? EMPTY : new UrlMappingsIndex(List.copyOf(merged));
    }

    List<Entry> getEntries() {
        return entries;
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Whether any loaded index identifies this runtime mapping.
     *
     * <p>Pattern, controller, action, namespace, plugin, and view have to name the same values, including
     * when both sides omit one. An omitted action, namespace, or plugin does not match a runtime value
     * that is present, so a partial entry cannot be treated as the winner.</p>
     */
    boolean describes(String pattern, Object controller, Object action, Object namespace, Object plugin,
            String httpMethod, Object view) {
        for (Entry entry : entries) {
            if (pattern.equals(entry.getPattern()) &&
                    identifies(entry.getController(), controller) &&
                    identifies(entry.getAction(), action) &&
                    identifies(entry.getNamespace(), namespace) &&
                    identifies(entry.getPlugin(), plugin) &&
                    sameMethod(entry.getHttpMethod(), httpMethod) &&
                    identifies(entry.getView(), view)) {
                return true;
            }
        }
        return false;
    }

    /**
     * An omitted index field identifies only a runtime value that is absent. A closure action is present,
     * so an entry that leaves the action out does not identify that mapping.
     */
    private static boolean identifies(String indexed, Object runtime) {
        if (indexed == null || indexed.isEmpty()) {
            return runtime == null;
        }
        return runtime instanceof String && indexed.equals(runtime);
    }

    private static boolean sameMethod(String indexed, String runtime) {
        if (indexed == null || indexed.isEmpty() || UrlMapping.ANY_HTTP_METHOD.equals(indexed)) {
            return true;
        }
        return indexed.equalsIgnoreCase(runtime);
    }

    private static List<Entry> read(URL url) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = url.openStream();
                Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        if (!FORMAT_VERSION.equals(properties.getProperty("format.version"))) {
            return List.of();
        }
        String artifactType = properties.getProperty("artifact.type");
        String artifactName = properties.getProperty("artifact.name");
        int count = mappingCount(properties);
        List<Entry> read = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String prefix = "mapping." + i + ".";
            String pattern = properties.getProperty(prefix + "pattern");
            if (pattern == null || !pattern.startsWith("/")) {
                continue;
            }
            read.add(new Entry(pattern,
                    blankToNull(properties.getProperty(prefix + "controller")),
                    blankToNull(properties.getProperty(prefix + "action")),
                    blankToNull(properties.getProperty(prefix + "method")),
                    blankToNull(properties.getProperty(prefix + "namespace")),
                    blankToNull(properties.getProperty(prefix + "plugin")),
                    blankToNull(properties.getProperty(prefix + "view")),
                    artifactType,
                    artifactName));
        }
        return read;
    }

    /**
     * The declared count has to match the pattern entries actually parsed. A larger count is rejected
     * before it is used as a list capacity.
     */
    private static int mappingCount(Properties properties) {
        String raw = properties.getProperty("mapping.count", "0");
        int declared;
        try {
            declared = Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException ex) {
            throw new IllegalArgumentException("mapping.count", ex);
        }
        if (declared < 0) {
            throw new IllegalArgumentException("mapping.count");
        }
        for (int i = 0; i < declared; i++) {
            if (properties.getProperty("mapping." + i + ".pattern") == null) {
                throw new IllegalArgumentException("mapping.count");
            }
        }
        for (String name : properties.stringPropertyNames()) {
            if (!name.startsWith("mapping.") || !name.endsWith(".pattern")) {
                continue;
            }
            String middle = name.substring("mapping.".length(), name.length() - ".pattern".length());
            int index;
            try {
                index = Integer.parseInt(middle);
            }
            catch (NumberFormatException ex) {
                throw new IllegalArgumentException("mapping.count", ex);
            }
            if (index < 0 || index >= declared) {
                throw new IllegalArgumentException("mapping.count");
            }
        }
        return declared;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /**
     * One mapping line from one classpath root.
     */
    static final class Entry {

        private final String pattern;
        private final String controller;
        private final String action;
        private final String httpMethod;
        private final String namespace;
        private final String plugin;
        private final String view;
        private final String artifactType;
        private final String artifactName;

        private Entry(String pattern, String controller, String action, String httpMethod, String namespace,
                String plugin, String view, String artifactType, String artifactName) {
            this.pattern = pattern;
            this.controller = controller;
            this.action = action;
            this.httpMethod = httpMethod;
            this.namespace = namespace;
            this.plugin = plugin;
            this.view = view;
            this.artifactType = artifactType;
            this.artifactName = artifactName;
        }

        String getPattern() {
            return pattern;
        }

        String getController() {
            return controller;
        }

        String getAction() {
            return action;
        }

        String getHttpMethod() {
            return httpMethod;
        }

        String getNamespace() {
            return namespace;
        }

        String getPlugin() {
            return plugin;
        }

        String getView() {
            return view;
        }

        String getArtifactType() {
            return artifactType;
        }

        String getArtifactName() {
            return artifactName;
        }
    }
}
