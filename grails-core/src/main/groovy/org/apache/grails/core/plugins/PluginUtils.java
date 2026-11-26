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
package org.apache.grails.core.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import grails.plugins.GrailsVersionUtils;
import grails.plugins.Plugin;
import grails.plugins.VersionComparator;
import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.GrailsPluginDescriptor;
import grails.plugins.metadata.PluginDefinition;
import grails.util.Environment;
import grails.util.GrailsNameUtils;
import org.grails.plugins.DefaultGrailsPlugin;

/**
 * Reads Grails plugin definitions: from the descriptors on a class path, and from the
 * {@link GrailsPluginDescriptor} of a plugin class registered directly.
 *
 * @since 7.0
 */
public final class PluginUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUtils.class);

    public static final String WILDCARD_OBSERVER_PATTERN = "*";
    public static final String PLUGINS_IMPORTS_PATH = "META-INF/grails/plugins.imports";
    public static final String PLUGIN_DESCRIPTORS_PREFIX = "META-INF/grails/plugins/";
    public static final String PLUGIN_PROPERTIES_FILE = "plugin.properties";
    public static final String PLUGIN_VIEWS_FILE = "views.properties";
    public static final String PLUGIN_YML_CONFIG = "plugin.yml";
    public static final String PLUGIN_GROOVY_CONFIG = "plugin.groovy";

    /** Configuration keys a plugin's configuration never contributes to the application's. */
    public static final List<String> DEFAULT_CONFIG_IGNORE_LIST = Arrays.asList("dataSource", "hibernate");

    private static final Pattern VALID_PLUGIN_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private PluginUtils() {
    }

    /**
     * Whether a name can be a plugin name: it is used as a directory below {@link #PLUGIN_DESCRIPTORS_PREFIX},
     * so it may not be blank or contain a path separator or {@code ..}.
     */
    public static boolean isValidPluginName(@Nullable String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return false;
        }
        String trimmed = pluginName.trim();
        return VALID_PLUGIN_NAME_PATTERN.matcher(trimmed).matches() && !trimmed.contains("..");
    }

    private static String validateNormalizedPluginName(String pluginName) {
        if (!isValidPluginName(pluginName)) {
            throw new IllegalArgumentException("Invalid Grails plugin name: [" + pluginName + "]");
        }
        return normalizePluginName(pluginName.trim());
    }

    private static String validateConfigFileName(String configFileName) {
        if (configFileName == null || configFileName.isBlank() ||
                configFileName.contains("/") || configFileName.contains("\\") || configFileName.contains("..")) {
            throw new IllegalArgumentException("Invalid plugin configuration file name: [" + configFileName + "]");
        }
        return configFileName;
    }

    /** @return {@code META-INF/grails/plugins/<name>/plugin.properties} */
    public static String getPluginDescriptorPath(String pluginName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" + PLUGIN_PROPERTIES_FILE;
    }

    /** @return {@code META-INF/grails/plugins/<name>/<configFileName>} */
    public static String getPluginConfigPath(String pluginName, String configFileName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" +
                validateConfigFileName(configFileName);
    }

    /** @return {@code META-INF/grails/plugins/<name>/views.properties} */
    public static String getPluginViewsPath(String pluginName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" + PLUGIN_VIEWS_FILE;
    }

    /** Converts a hyphenated name ({@code spring-security-core}) to its property form ({@code springSecurityCore}). */
    public static String normalizePluginName(String name) {
        if (name.indexOf('-') > -1) {
            return GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(name);
        }
        return name;
    }

    /**
     * Whether a class is a Grails plugin: a concrete {@link Plugin} subclass annotated with
     * {@link GrailsPluginDescriptor}.
     */
    public static boolean isGrailsPlugin(@Nullable Class<?> pluginClass) {
        return pluginClass != null &&
                Plugin.class.isAssignableFrom(pluginClass) &&
                !Modifier.isAbstract(pluginClass.getModifiers()) &&
                pluginClass.isAnnotationPresent(GrailsPluginDescriptor.class);
    }

    /** @return the logical name a plugin class declares, or derives from its class name */
    public static String getLogicalPluginName(Class<?> pluginClass) {
        GrailsPluginDescriptor annotation = pluginClass.getAnnotation(GrailsPluginDescriptor.class);
        return PluginDefinition.deriveName(pluginClass.getName(), annotation != null ? annotation.name() : null);
    }

    /**
     * Reads the names listed in every {@code META-INF/grails/plugins.imports} on a class path.
     *
     * @throws PluginException if a file cannot be read or lists an invalid name
     */
    public static Set<String> scanPluginImports(ClassLoader classLoader) {
        Set<String> pluginNames = new LinkedHashSet<>();
        for (URL url : resources(classLoader, PLUGINS_IMPORTS_PATH)) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int commentIdx = line.indexOf('#');
                    String trimmed = (commentIdx >= 0 ? line.substring(0, commentIdx) : line).trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    if (!isValidPluginName(trimmed)) {
                        throw new PluginException("Invalid Grails plugin name [" + trimmed + "] in " + url);
                    }
                    pluginNames.add(normalizePluginName(trimmed));
                }
            }
            catch (IOException e) {
                throw new PluginException("Could not read " + url, e);
            }
        }
        return pluginNames;
    }

    /**
     * Reads the descriptor of every plugin listed in a {@code META-INF/grails/plugins.imports} on a class path,
     * without loading the plugins' classes. A name listed by more than one jar yields one entry per jar.
     *
     * @return each {@code plugin.properties} found, with the definition read from it, in class path order
     * @throws PluginException if a listed plugin has no descriptor, or a descriptor cannot be read
     */
    public static Map<Resource, PluginDefinition> scanPluginDescriptorResources(ClassLoader classLoader) {
        Map<Resource, PluginDefinition> descriptors = new LinkedHashMap<>();
        for (String pluginName : scanPluginImports(classLoader)) {
            String descriptorPath = getPluginDescriptorPath(pluginName);
            List<URL> urls = resources(classLoader, descriptorPath);
            if (urls.isEmpty()) {
                throw new PluginException("Grails plugin [" + pluginName + "] is listed in " + PLUGINS_IMPORTS_PATH +
                        " but has no " + descriptorPath);
            }
            for (URL url : urls) {
                descriptors.put(new UrlResource(url), readDefinition(pluginName, url));
            }
        }
        return descriptors;
    }

    private static PluginDefinition readDefinition(String pluginName, URL url) {
        try (InputStream input = url.openStream();
             InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.load(reader);
            return PluginDefinition.fromProperties(pluginName, properties);
        }
        catch (IOException | IllegalArgumentException e) {
            throw new PluginException("Invalid Grails plugin descriptor " + url + ": " + e.getMessage(), e);
        }
    }

    private static List<URL> resources(ClassLoader classLoader, String path) {
        try {
            List<URL> urls = new ArrayList<>();
            Enumeration<URL> found = classLoader.getResources(path);
            while (found.hasMoreElements()) {
                urls.add(found.nextElement());
            }
            return urls;
        }
        catch (IOException e) {
            throw new PluginException("Could not search the class path for " + path, e);
        }
    }

    /**
     * The definition of a plugin class registered directly, read from its {@link GrailsPluginDescriptor}.
     * Its version is the one the class's own descriptor records, when the class path holds it.
     *
     * @throws PluginException if the class is not a Grails plugin
     */
    public static PluginDefinition definitionOf(Class<?> pluginClass) {
        if (!isGrailsPlugin(pluginClass)) {
            throw new PluginException("Class [" + pluginClass.getName() + "] is not a Grails plugin: a plugin is a " +
                    "concrete grails.plugins.Plugin subclass annotated with @GrailsPluginDescriptor");
        }
        GrailsPluginDescriptor annotation = pluginClass.getAnnotation(GrailsPluginDescriptor.class);
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("name", annotation.name());
        attributes.put("grailsVersion", annotation.grailsVersion());
        attributes.put("loadAfter", List.of(annotation.loadAfter()));
        attributes.put("loadBefore", List.of(annotation.loadBefore()));
        attributes.put("dependsOn", List.of(annotation.dependsOn()));
        attributes.put("evict", List.of(annotation.evict()));
        attributes.put("observe", List.of(annotation.observe()));
        attributes.put("environmentsIncludes", List.of(annotation.environmentsIncludes()));
        attributes.put("environmentsExcludes", List.of(annotation.environmentsExcludes()));
        attributes.put("watchedResources", List.of(annotation.watchedResources()));
        attributes.put("pluginExcludes", List.of(annotation.pluginExcludes()));
        attributes.put("providedArtefacts", Arrays.stream(annotation.providedArtefacts()).map(Class::getName).toList());
        attributes.put("enabled", annotation.enabled());
        return PluginDefinition.fromDeclaration(pluginClass.getName(), attributes, publishedVersion(pluginClass), null, Map.of());
    }

    private static @Nullable String publishedVersion(Class<?> pluginClass) {
        ClassLoader loader = pluginClass.getClassLoader();
        if (loader == null) {
            return null;
        }
        String name = getLogicalPluginName(pluginClass);
        URL url = loader.getResource(getPluginDescriptorPath(name));
        if (url == null) {
            return null;
        }
        PluginDefinition published = readDefinition(name, url);
        return pluginClass.getName().equals(published.getPluginClass()) ? published.getPluginVersion() : null;
    }

    /**
     * The plugin a class registered directly is: its definition is read from its {@link GrailsPluginDescriptor},
     * and its configuration from its class loader.
     *
     * @throws PluginException if the class is not a Grails plugin
     */
    public static PluginInfo createPluginInfo(Class<?> pluginClass) {
        PluginDefinition definition = definitionOf(pluginClass);
        return new PluginInfo(definition, pluginClass, null,
                readPluginConfiguration(definition.getPluginName(), pluginClass.getClassLoader()));
    }

    /**
     * The plugin a descriptor on the class path describes.
     *
     * @param pluginClass the class the definition names, loaded
     * @param definition the definition read from the descriptor
     * @param descriptorResource the descriptor, or {@code null} when the definition was not read from one
     * @throws PluginException if the class is not the definition's Grails plugin class
     */
    public static PluginInfo createPluginInfo(Class<?> pluginClass, PluginDefinition definition,
            @Nullable Resource descriptorResource) {
        return new PluginInfo(definition, pluginClass, descriptorResource,
                readPluginConfiguration(definition.getPluginName(), pluginClass.getClassLoader()));
    }

    /**
     * Finds a plugin's configuration, {@code META-INF/grails/plugins/<name>/plugin.yml} or {@code plugin.groovy}.
     *
     * @throws PluginException if the plugin has both
     */
    public static @Nullable Resource readPluginConfiguration(String pluginName, @Nullable ClassLoader classLoader) {
        ClassLoader loader = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        if (loader == null || !isValidPluginName(pluginName)) {
            return null;
        }
        URL yml = loader.getResource(getPluginConfigPath(pluginName, PLUGIN_YML_CONFIG));
        URL groovy = loader.getResource(getPluginConfigPath(pluginName, PLUGIN_GROOVY_CONFIG));
        if (yml != null && groovy != null) {
            throw new PluginException("A plugin [" + pluginName + "] may define a plugin.yml or a plugin.groovy, but not both");
        }
        URL found = yml != null ? yml : groovy;
        return found != null ? new UrlResource(found) : null;
    }

    static Map<String, Set<Object>> buildEnvironmentsMap(List<String> includes, List<String> excludes) {
        Map<String, Set<Object>> result = new HashMap<>();
        if (!includes.isEmpty()) {
            Set<Object> values = new HashSet<>();
            includes.forEach(name -> values.add(environmentName(name)));
            result.put(DefaultGrailsPlugin.INCLUDES, values);
        }
        if (!excludes.isEmpty()) {
            Set<Object> values = new HashSet<>();
            excludes.forEach(name -> values.add(environmentName(name)));
            result.put(DefaultGrailsPlugin.EXCLUDES, values);
        }
        return result;
    }

    private static String environmentName(String name) {
        Environment environment = Environment.getEnvironment(name);
        return environment != null ? environment.getName() : name;
    }

    /** Whether a value passes an include/exclude map such as {@link #buildEnvironmentsMap}'s. */
    public static boolean supportsValueInIncludeExcludeMap(Map<String, Set<Object>> includeExcludeMap, Object value) {
        if (includeExcludeMap.isEmpty()) {
            return true;
        }
        Set<Object> includes = includeExcludeMap.get(DefaultGrailsPlugin.INCLUDES);
        if (includes != null) {
            return includes.contains(value);
        }
        Set<Object> excludes = includeExcludeMap.get(DefaultGrailsPlugin.EXCLUDES);
        return !(excludes != null && excludes.contains(value));
    }

    /**
     * Whether a plugin supports a Grails version. A plugin without a range supports every version; one that
     * does not support this version is reported, and the caller decides what to do with it.
     */
    public static boolean isPluginVersionCompatible(String pluginVersion, String pluginSupportedVersionRange, String grailsVersion, String pluginDescription) {
        if (pluginSupportedVersionRange == null || pluginSupportedVersionRange.contains("@")) {
            LOG.debug("Plugin grails version is null or containing '@'. Compatibility check skipped.");
            return true;
        }

        var pluginMinGrailsVersion = GrailsVersionUtils.getLowerVersion(pluginSupportedVersionRange);
        var pluginMaxGrailsVersion = GrailsVersionUtils.getUpperVersion(pluginSupportedVersionRange);
        if (grailsVersion == null) {
            return true;
        }

        if (pluginMinGrailsVersion.equals("*")) {
            LOG.error("grailsVersion not formatted as expected, unable to determine compatibility.");
            return false;
        }

        var comparator = new VersionComparator();

        if (pluginMinGrailsVersion.equals(pluginMaxGrailsVersion)) {
            if (!grailsVersion.equals(pluginMinGrailsVersion)) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is not equal" +
                                " to the one that plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }
        }
        if (!pluginMaxGrailsVersion.equals("*")) {
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }
        } else {
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }

            if (comparator.compare(pluginMaxGrailsVersion, grailsVersion) < 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is greater" +
                                " than the plugins max specified. Plugin is compatible with Grails versions {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }
        }

        return true;
    }
}
