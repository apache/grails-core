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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.grails.core.plugins.PluginUtils;

/**
 * Reads every {@link I18nDescriptor} on the classpath.
 *
 * <p>Discovery is an exact-name {@link ClassLoader#getResources} lookup, the same mechanism Spring
 * Boot uses for {@code AutoConfiguration.imports}. That matters for GraalVM: a wildcard
 * {@code classpath*:*.properties} scan cannot be resolved in a native image, whereas an exact name
 * needs only a single resource hint.</p>
 *
 * <p>Reading order is <em>not</em> meaningful. Classloader enumeration order is not portable across
 * Gradle test runtimes, executable jars, layered jars and native images, and it cannot distinguish an
 * application from a plugin at all. Precedence therefore comes from the descriptor contents — see
 * {@link EffectiveI18nDescriptors}.</p>
 *
 * @since 8.0
 */
public final class I18nDescriptors {

    /** Location of the descriptor within an application or plugin artifact. */
    public static final String DESCRIPTOR_PATH = "META-INF/grails/i18n.properties";

    /** The only descriptor format this release understands. */
    public static final String SUPPORTED_FORMAT_VERSION = "1";

    private static final String FORMAT_VERSION = "format.version";

    private static final String ARTIFACT_TYPE = "artifact.type";

    private static final String ARTIFACT_NAME = "artifact.name";

    private static final String ARTIFACT_VERSION = "artifact.version";

    private static final String BASENAMES = "basenames";

    private static final String LOCALES = "locales";

    private I18nDescriptors() {
    }

    /**
     * Loads every descriptor visible to the supplied class loader.
     *
     * @param classLoader the class loader to search; the thread context loader when {@code null}
     * @return the descriptors, in an order that carries no meaning
     * @throws IllegalStateException when a descriptor is malformed, or when the classpath is
     *         ambiguous — more than one application descriptor, or two plugins sharing a name
     */
    public static List<I18nDescriptor> load(ClassLoader classLoader) {
        ClassLoader loader = (classLoader != null) ? classLoader : Thread.currentThread().getContextClassLoader();
        List<I18nDescriptor> descriptors = new ArrayList<>();
        try {
            Enumeration<URL> urls = loader.getResources(DESCRIPTOR_PATH);
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                descriptors.add(read(url));
            }
        }
        catch (IOException ex) {
            throw new UncheckedIOException("Unable to read " + DESCRIPTOR_PATH + " from the classpath", ex);
        }
        rejectAmbiguousClasspath(descriptors);
        return List.copyOf(descriptors);
    }

    private static I18nDescriptor read(URL url) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = url.openStream()) {
            properties.load(input);
        }

        String formatVersion = required(properties, FORMAT_VERSION, url);
        if (!SUPPORTED_FORMAT_VERSION.equals(formatVersion)) {
            throw new IllegalStateException("Unsupported i18n descriptor format version '" + formatVersion +
                    "' at " + url + ". This Grails version understands version " + SUPPORTED_FORMAT_VERSION +
                    "; the artifact was built by a newer Grails Gradle plugin.");
        }

        String type = required(properties, ARTIFACT_TYPE, url);
        if (!I18nDescriptor.TYPE_APPLICATION.equals(type) && !I18nDescriptor.TYPE_PLUGIN.equals(type)) {
            throw new IllegalStateException("Invalid " + ARTIFACT_TYPE + " '" + type + "' at " + url +
                    ". Expected '" + I18nDescriptor.TYPE_APPLICATION + "' or '" + I18nDescriptor.TYPE_PLUGIN + "'.");
        }

        return new I18nDescriptor(type, required(properties, ARTIFACT_NAME, url),
                properties.getProperty(ARTIFACT_VERSION),
                split(required(properties, BASENAMES, url)), split(properties.getProperty(LOCALES)));
    }

    private static String required(Properties properties, String key, URL url) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Malformed i18n descriptor at " + url + ": missing '" + key + "'. " +
                    "The descriptor is generated by the Grails Gradle plugin and should not be edited by hand.");
        }
        return value.trim();
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(entry -> !entry.isEmpty()).toList();
    }

    /**
     * Rejects classpaths where precedence would be decided by classloader enumeration order.
     *
     * <p>Two plugins sharing a name, or two application descriptors (which layered jars and test
     * fixtures can produce), would otherwise silently let whichever jar happened to be enumerated
     * first win — exactly the nondeterminism this design removes.</p>
     */
    private static void rejectAmbiguousClasspath(List<I18nDescriptor> descriptors) {
        List<String> applications = descriptors.stream().filter(I18nDescriptor::isApplication)
                .map(I18nDescriptor::name).toList();
        if (applications.size() > 1) {
            throw new IllegalStateException("Found " + applications.size() + " application i18n descriptors on the " +
                    "classpath (" + String.join(", ", applications) + "). Exactly one application may contribute " +
                    "message bundles; a second usually means an application jar is on the classpath of another " +
                    "application, or a test fixture ships its own descriptor.");
        }

        // Counted by normalised name, because that is the form descriptors are matched to discovered
        // plugins by. Comparing the raw names would let 'spring-security-core' and 'springSecurityCore'
        // through here, only for one of them to be dropped without a word when they collapse to the
        // same key downstream.
        Map<String, List<String>> pluginsByNormalisedName = new LinkedHashMap<>();
        descriptors.stream().filter(descriptor -> !descriptor.isApplication())
                .forEach(descriptor -> pluginsByNormalisedName
                        .computeIfAbsent(PluginUtils.normalizePluginName(descriptor.name()),
                                normalised -> new ArrayList<>())
                        .add(descriptor.name()));
        List<String> duplicates = pluginsByNormalisedName.values().stream().filter(names -> names.size() > 1)
                .map(names -> String.join(" and ", names)).toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("More than one i18n descriptor for plugin(s) " +
                    String.join("; ", duplicates) + ". Descriptors are matched to plugins by name, so duplicates " +
                    "would make message precedence depend on classpath order. Names are compared in their " +
                    "normalised form, so a hyphenated and a camel-case spelling are the same plugin. This usually " +
                    "means two versions of the same plugin are on the classpath.");
        }
    }
}
