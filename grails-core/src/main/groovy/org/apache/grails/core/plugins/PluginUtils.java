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
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import groovy.transform.Internal;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import grails.io.IOUtils;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsVersionUtils;
import grails.plugins.Plugin;
import grails.plugins.VersionComparator;
import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.GrailsPluginDescriptor;
import grails.util.Environment;
import grails.util.GrailsClassUtils;
import grails.util.GrailsNameUtils;
import org.grails.plugins.DefaultGrailsPlugin;

/**
 * Static utility methods supporting Grails plugin discovery, metadata extraction, configuration lookup,
 * and compatibility checks.
 *
 * <p>This class centralizes the low-level operations used by bootstrap-time discovery, including scanning
 * {@code META-INF/grails/plugins.imports} and per-plugin {@code META-INF/grails/plugins/<name>/plugin.properties}
 * descriptors, deriving logical plugin names, reading plugin metadata from annotations, descriptors, or plugin
 * classes, and evaluating environment and version constraints.</p>
 */
public final class PluginUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUtils.class);

    /**
     * The wildcard pattern used to indicate that a plugin observes all other plugins,
     * i.e., it should be notified of events from all plugins regardless of name.
     */
    public static final String WILDCARD_OBSERVER_PATTERN = "*";

    /**
     * The classpath location of the Grails plugin discovery index file. Each non-blank, non-comment line
     * names a logical plugin whose descriptor lives at {@code META-INF/grails/plugins/<name>/plugin.properties}.
     *
     * @since 9.0
     */
    public static final String PLUGINS_IMPORTS_PATH = "META-INF/grails/plugins.imports";

    /**
     * The classpath prefix under which each plugin stores its namespaced descriptor, configuration, and view manifest.
     *
     * @since 9.0
     */
    public static final String PLUGIN_DESCRIPTORS_PREFIX = "META-INF/grails/plugins/";

    /**
     * The filename of each plugin's generated properties descriptor.
     *
     * @since 9.0
     */
    public static final String PLUGIN_PROPERTIES_FILE = "plugin.properties";

    /**
     * The filename of each plugin's precompiled GSP view manifest inside its plugin namespace.
     *
     * @since 9.0
     */
    public static final String PLUGIN_VIEWS_FILE = "views.properties";

    /**
     * The filename for YAML-based plugin configuration.
     */
    public static final String PLUGIN_YML_CONFIG = "plugin.yml";

    /**
     * The filename for Groovy ConfigSlurper-based plugin configuration.
     */
    public static final String PLUGIN_GROOVY_CONFIG = "plugin.groovy";

    /**
     * Default config keys to ignore when loading plugin configuration.
     */
    public static final List<String> DEFAULT_CONFIG_IGNORE_LIST = Arrays.asList("dataSource", "hibernate");

    /**
     * The plugin property that declares the supported Grails version expression.
     */
    public static final String PLUGIN_GRAILS_VERSION_FIELD = "grailsVersion";

    /**
     * The relative lookup path for Groovy-based plugin configuration.
     */
    public static final String PLUGIN_GROOVY_CONFIG_PATH = "/" + PLUGIN_GROOVY_CONFIG;

    /**
     * The relative lookup path for YAML-based plugin configuration.
     */
    public static final String PLUGIN_YML_CONFIG_PATH = "/" + PLUGIN_YML_CONFIG;

    /**
     * Where the build keeps a copy of each plugin's configuration for an application running on code
     * generated ahead of time, in a directory named after the plugin class.
     *
     * @since 9.0
     * @see #getGeneratedConfigurationPath(String, String)
     */
    public static final String GENERATED_CONFIG_PATH = "META-INF/grails/plugin-configuration/";

    /**
     * Strict pattern for logical plugin names used as path segments under {@link #PLUGIN_DESCRIPTORS_PREFIX}.
     */
    private static final Pattern VALID_PLUGIN_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    /**
     * The legacy classpath location for XML plugin descriptors generated prior to Grails 9.
     *
     * @deprecated Replaced in Grails 9.0 by {@link #PLUGINS_IMPORTS_PATH} and
     *             {@code META-INF/grails/plugins/<name>/plugin.properties}. Read-only fallback support
     *             will be removed in Grails 10.0.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static final String PLUGIN_XML_PATTERN = "META-INF/grails-plugin.xml";

    /**
     * The legacy classpath location for XML plugin descriptors generated prior to Grails 9.
     *
     * @deprecated Replaced in Grails 9.0 by {@link #PLUGINS_IMPORTS_PATH} and
     *             {@code META-INF/grails/plugins/<name>/plugin.properties}. Read-only fallback support
     *             will be removed in Grails 10.0.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static final String LEGACY_PLUGIN_DESCRIPTOR_PATH = PLUGIN_XML_PATTERN;

    /**
     * The naming suffix required for traditional Grails plugin implementation classes.
     */
    public static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";

    private PluginUtils() {
        // prevent instantiation - all methods are static
    }

    /**
     * Returns {@code true} if the given plugin name is safe to use as a single path segment under
     * {@link #PLUGIN_DESCRIPTORS_PREFIX}.
     *
     * @param pluginName the raw or normalized plugin name
     * @return {@code true} if valid and free of path traversal characters
     * @since 9.0
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

    /**
     * Returns the classpath location of a plugin's descriptor properties file.
     *
     * @param pluginName the logical plugin name
     * @return {@code META-INF/grails/plugins/<name>/plugin.properties}
     * @since 9.0
     */
    public static String getPluginDescriptorPath(String pluginName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" + PLUGIN_PROPERTIES_FILE;
    }

    /**
     * Returns the classpath location of a plugin's namespaced configuration file.
     *
     * @param pluginName the logical plugin name
     * @param configFileName {@link #PLUGIN_YML_CONFIG} or {@link #PLUGIN_GROOVY_CONFIG}
     * @return {@code META-INF/grails/plugins/<name>/<configFileName>}
     * @since 9.0
     */
    public static String getPluginConfigPath(String pluginName, String configFileName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" +
                validateConfigFileName(configFileName);
    }

    /**
     * Returns the classpath location of a plugin's precompiled GSP view manifest.
     *
     * @param pluginName the logical plugin name
     * @return {@code META-INF/grails/plugins/<name>/views.properties}
     * @since 9.0
     */
    public static String getPluginViewsPath(String pluginName) {
        return PLUGIN_DESCRIPTORS_PREFIX + validateNormalizedPluginName(pluginName) + "/" + PLUGIN_VIEWS_FILE;
    }

    /**
     * Scans all {@code META-INF/grails/plugins.imports} index resources visible to the supplied class loader
     * and loads each named plugin's {@code META-INF/grails/plugins/<name>/plugin.properties} descriptor.
     *
     * <p>As a deprecated Grails 9 transition fallback (scheduled for removal in Grails 10), legacy
     * {@code META-INF/grails-plugin.xml} descriptors are also read for plugins that were not already
     * discovered through {@code META-INF/grails/plugins.imports}.</p>
     *
     * <p>Invalid descriptors are logged and skipped so discovery of remaining plugins can continue.</p>
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of {@link PluginDescriptor} records discovered on the classpath
     */
    public static List<PluginDescriptor> scanPluginDescriptorResources(ClassLoader classLoader) {
        List<PluginDescriptor> descriptors = new ArrayList<>();
        Set<String> discoveredPluginClasses = new HashSet<>();

        Set<String> pluginNames = scanPluginImports(classLoader);
        for (String pluginName : pluginNames) {
            String descriptorPath = getPluginDescriptorPath(pluginName);
            try {
                Enumeration<URL> resources = classLoader.getResources(descriptorPath);
                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    try (InputStream input = url.openStream();
                         InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                        Properties props = new Properties();
                        props.load(reader);
                        Resource descriptorResource = new UrlResource(url);
                        PluginDescriptor descriptor = PluginDescriptor.fromProperties(pluginName, descriptorResource, props);
                        descriptors.add(descriptor);
                        discoveredPluginClasses.addAll(descriptor.getProvidedPlugins());
                    } catch (IOException | IllegalArgumentException e) {
                        LOG.debug("Error parsing plugin descriptor at [{}]: {}", url, e.getMessage());
                    }
                }
            } catch (IOException e) {
                LOG.debug("Error locating plugin descriptor [{}]: {}", descriptorPath, e.getMessage());
            }
        }

        scanLegacyXmlPluginDescriptors(classLoader, descriptors, discoveredPluginClasses);
        return descriptors;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static void scanLegacyXmlPluginDescriptors(
            ClassLoader classLoader,
            List<PluginDescriptor> descriptors,
            Set<String> discoveredPluginClasses) {
        try {
            Enumeration<URL> legacyResources = classLoader.getResources(LEGACY_PLUGIN_DESCRIPTOR_PATH);
            if (!legacyResources.hasMoreElements()) {
                return;
            }
            SAXParserFactory factory = createSecureSaxParserFactory();
            while (legacyResources.hasMoreElements()) {
                URL url = legacyResources.nextElement();
                try (InputStream input = url.openStream()) {
                    SAXParser parser = factory.newSAXParser();
                    PluginXmlHandler handler = new PluginXmlHandler();
                    parser.parse(input, handler);

                    List<String> pluginClasses = handler.getPluginClassNames();
                    if (pluginClasses.isEmpty()) {
                        continue;
                    }
                    List<String> remainingClasses = new ArrayList<>();
                    for (String pluginClass : pluginClasses) {
                        if (!discoveredPluginClasses.contains(pluginClass)) {
                            remainingClasses.add(pluginClass);
                        }
                    }
                    if (remainingClasses.isEmpty()) {
                        continue;
                    }

                    String descriptorName = null;
                    if (remainingClasses.size() == 1) {
                        String className = remainingClasses.get(0);
                        descriptorName = getLogicalPluginNameFromClassName(className.substring(className.lastIndexOf('.') + 1));
                    }
                    String label = String.join(", ", remainingClasses);
                    LOG.warn("Plugin [{}] was discovered via legacy {} at [{}], which is deprecated in Grails 9.0 " +
                                    "and will be removed in Grails 10.0. Rebuild this plugin against Grails 9 to " +
                                    "generate {} and META-INF/grails/plugins/<name>/plugin.properties.",
                            label, LEGACY_PLUGIN_DESCRIPTOR_PATH, url, PLUGINS_IMPORTS_PATH);

                    Resource descriptorResource = new UrlResource(url);
                    descriptors.add(new PluginDescriptor(
                            descriptorResource,
                            descriptorName,
                            handler.getPluginVersion(),
                            null,
                            remainingClasses,
                            handler.getProvidedClasses(),
                            Map.of(),
                            List.of(),
                            List.of(),
                            Map.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            List.of(),
                            true,
                            false
                    ));
                    discoveredPluginClasses.addAll(remainingClasses);
                } catch (IOException | ParserConfigurationException | SAXException e) {
                    LOG.debug("Error parsing legacy plugin descriptor at [{}]: {}", url, e.getMessage());
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOG.debug("Error scanning legacy plugin descriptors: {}", e.getMessage());
        }
    }

    private static SAXParserFactory createSecureSaxParserFactory()
            throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        return factory;
    }

    /**
     * Reads all {@code META-INF/grails/plugins.imports} files visible to the supplied class loader and
     * returns the distinct logical plugin names in encounter order.
     *
     * @param classLoader the class loader to scan
     * @return distinct logical plugin names declared in {@code META-INF/grails/plugins.imports}
     * @since 9.0
     */
    public static Set<String> scanPluginImports(ClassLoader classLoader) {
        Set<String> pluginNames = new LinkedHashSet<>();
        try {
            Enumeration<URL> importResources = classLoader.getResources(PLUGINS_IMPORTS_PATH);
            while (importResources.hasMoreElements()) {
                URL url = importResources.nextElement();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(url.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        int commentIdx = line.indexOf('#');
                        if (commentIdx >= 0) {
                            line = line.substring(0, commentIdx);
                        }
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            if (isValidPluginName(trimmed)) {
                                pluginNames.add(normalizePluginName(trimmed));
                            } else {
                                LOG.warn("Ignoring invalid plugin name [{}] in {} at [{}]",
                                        trimmed, PLUGINS_IMPORTS_PATH, url);
                            }
                        }
                    }
                } catch (IOException e) {
                    LOG.debug("Error reading plugin imports at [{}]: {}", url, e.getMessage());
                }
            }
        } catch (IOException e) {
            LOG.debug("Error scanning for plugin imports: {}", e.getMessage());
        }
        return pluginNames;
    }

    /**
     * Creates a {@link PluginInfo} for a single plugin class using a synthetic descriptor.
     *
     * @param pluginClass the plugin implementation class
     * @param descriptorResource the descriptor resource associated with the plugin, if any
     * @param dynamic whether the plugin originated from a dynamic plugin configuration
     * @return the created plugin info
     */
    public static PluginInfo createPluginInfo(Class<?> pluginClass, @Nullable Resource descriptorResource, boolean dynamic) {
        List<String> pluginClassNames = List.of(pluginClass.getName());
        return createPluginInfoByDescriptor(pluginClass, new PluginDescriptor(descriptorResource, pluginClassNames, List.of()), dynamic);
    }

    /**
     * Creates a {@link PluginInfo} for a plugin class using the supplied descriptor.
     *
     * @param pluginClass the plugin implementation class
     * @param descriptor the descriptor that produced the plugin
     * @param dynamic whether the plugin originated from a dynamic plugin configuration
     * @return the created plugin info
     */
    public static PluginInfo createPluginInfoByDescriptor(Class<?> pluginClass, PluginDescriptor descriptor, boolean dynamic) {
        PluginMetadata metadata = PluginUtils.extractPluginMetadata(descriptor, pluginClass);
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Could not extract plugin metadata for class [" + pluginClass.getName() + "]");
        }
        ClassLoader loader = pluginClass.getClassLoader();
        Resource configResource = PluginUtils.readPluginConfiguration(metadata.getName(), pluginClass, loader);
        return new PluginInfo(
                descriptor,
                metadata,
                configResource,
                dynamic
        );
    }

    /**
     * Creates a {@link PluginInfo} for a descriptor-less starter plugin.
     *
     * @param descriptor the plugin descriptor
     * @param classLoader optional class loader used to locate namespaced plugin configuration
     * @param dynamic whether the plugin originated from a dynamic plugin configuration
     * @return the created plugin info
     * @since 9.0
     */
    public static PluginInfo createPluginInfoForDescriptor(
            PluginDescriptor descriptor,
            @Nullable ClassLoader classLoader,
            boolean dynamic) {
        PluginMetadata metadata = PluginUtils.extractPluginMetadata(descriptor, null);
        if (metadata == null) {
            throw new IllegalArgumentException(
                    "Could not extract plugin metadata for descriptor [" + descriptor.getPluginName() + "]");
        }
        Resource configResource = PluginUtils.readPluginConfiguration(metadata.getName(), null, classLoader);
        return new PluginInfo(
                descriptor,
                metadata,
                configResource,
                dynamic
        );
    }

    /**
     * Normalizes a plugin name into a Grails logical property form.
     *
     * <p>Hyphen-separated names are converted to lower camel case so lookups can match either external plugin names
     * such as {@code some-plugin} or logical names such as {@code somePlugin}.</p>
     *
     * @param name the plugin name to normalize
     * @return the normalized plugin name
     */
    public static String normalizePluginName(String name) {
        if (name.indexOf('-') > -1) {
            return GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(name);
        }
        return name;
    }

    /**
     * Convenience method that scans all classpath plugin descriptors and returns the declared plugin class names.
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of fully qualified plugin class names discovered on the classpath
     */
    public static List<String> scanPluginDescriptors(ClassLoader classLoader) {
        List<PluginDescriptor> descriptors = scanPluginDescriptorResources(classLoader);
        List<String> pluginClassNames = new ArrayList<>();
        for (PluginDescriptor descriptor : descriptors) {
            pluginClassNames.addAll(descriptor.getProvidedPlugins());
        }
        return pluginClassNames;
    }

    /**
     * Derives the logical plugin name from the plugin class, preferring {@link GrailsPluginDescriptor#name()}
     * when present and otherwise following the Grails {@code *GrailsPlugin} naming convention.
     *
     * @param pluginClass the plugin class
     * @return the logical plugin name
     */
    public static String getLogicalPluginName(Class<?> pluginClass) {
        GrailsPluginDescriptor annotation = pluginClass.getAnnotation(GrailsPluginDescriptor.class);
        if (annotation != null && !annotation.name().isBlank()) {
            return normalizePluginName(annotation.name().trim());
        }
        return getLogicalPluginNameFromClassName(pluginClass.getSimpleName());
    }

    /**
     * Derives the logical plugin name from a plugin class name.
     *
     * @param name the plugin class simple name
     * @return the logical plugin name with the {@link #GRAILS_PLUGIN_SUFFIX} removed
     */
    public static String getLogicalPluginNameFromClassName(String name) {
        if (name.endsWith(GRAILS_PLUGIN_SUFFIX)) {
            return GrailsNameUtils.getLogicalPropertyName(name, GRAILS_PLUGIN_SUFFIX);
        }
        return GrailsNameUtils.getPropertyName(name);
    }

    /**
     * Extracts {@link PluginMetadata} from a {@link PluginDescriptor} and optional plugin class.
     *
     * <p>When {@code pluginClass} is {@code null} (descriptor-less starter plugin) or when the descriptor
     * carries complete metadata (such as from {@link GrailsPluginDescriptor}), metadata is constructed
     * directly without instantiating any class via reflection. Otherwise metadata is extracted from the
     * plugin class, falling back to the descriptor's version when the class does not declare one.</p>
     *
     * @param descriptor the parsed plugin descriptor
     * @param pluginClass the loaded plugin class, or {@code null} for descriptor-less plugins
     * @return the extracted {@link PluginMetadata}, or {@code null} if invalid
     * @since 9.0
     */
    public static @Nullable PluginMetadata extractPluginMetadata(
            PluginDescriptor descriptor,
            @Nullable Class<?> pluginClass) {
        if (pluginClass == null) {
            return extractPluginMetadataFromDescriptor(descriptor, null);
        }
        String descriptorNameOverride = (descriptor.getProvidedPlugins().size() <= 1 &&
                descriptor.getPluginName() != null && !descriptor.getPluginName().isBlank()) ?
                normalizePluginName(descriptor.getPluginName()) :
                null;
        if (pluginClass.isAnnotationPresent(GrailsPluginDescriptor.class)) {
            return extractPluginMetadataFromAnnotation(
                    pluginClass, descriptor.getPluginVersion(), descriptor.getGrailsVersionRange(), descriptorNameOverride);
        }
        if (!isGrailsPluginClassNamedCorrectly(pluginClass)) {
            return null;
        }
        if (descriptor.isMetadataComplete() && descriptor.getPluginName() != null) {
            return extractPluginMetadataFromDescriptor(descriptor, pluginClass);
        }
        return extractPluginMetadata(pluginClass, descriptor.getPluginVersion(), descriptorNameOverride);
    }

    /**
     * Extracts plugin metadata from a plugin class without requiring a full
     * {@link grails.core.GrailsApplication} or plugin manager.
     *
     * <p>Classes annotated with {@link GrailsPluginDescriptor} are read directly from their annotation
     * without reflection instantiation. Legacy {@code *GrailsPlugin} classes are instantiated and inspected
     * via a {@link BeanWrapper}.</p>
     *
     * @param pluginClass the plugin class to extract metadata from
     * @return a {@link PluginMetadata} instance, or {@code null} if the class is not a valid Grails plugin
     */
    public static @Nullable PluginMetadata extractPluginMetadata(Class<?> pluginClass) {
        return extractPluginMetadata(pluginClass, null, null);
    }

    private static @Nullable PluginMetadata extractPluginMetadata(
            Class<?> pluginClass,
            @Nullable String fallbackVersion,
            @Nullable String overrideName) {
        if (!isGrailsPluginClassNamedCorrectly(pluginClass)) {
            return null;
        }
        if (pluginClass.isAnnotationPresent(GrailsPluginDescriptor.class)) {
            return extractPluginMetadataFromAnnotation(pluginClass, fallbackVersion, null, overrideName);
        }

        String pluginName = (overrideName != null && !overrideName.isBlank()) ?
                normalizePluginName(overrideName) :
                getLogicalPluginName(pluginClass);
        String pluginVersion;
        String grailsVersion;

        String[] loadAfterNames;
        String[] loadBeforeNames;
        String[] evictions;
        String[] observedPluginNames;
        PluginDependencies dependencies;
        Map<String, Set<Object>> environments;
        String status;

        Object pluginInstance;
        try {
            pluginInstance = pluginClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalStateException("Could not instantiate plugin [" + pluginName + "]: ", e);
        }

        var beanWrapper = new BeanWrapperImpl(pluginInstance);

        pluginVersion = evaluatePluginVersion(beanWrapper, pluginInstance, pluginName, fallbackVersion);
        grailsVersion = getPluginGrailsVersionRange(pluginInstance, pluginName);
        dependencies = evaluatePluginDependencies(beanWrapper, pluginInstance);
        loadAfterNames = evaluatePluginLoadAfters(beanWrapper, pluginInstance);
        loadBeforeNames = evaluatePluginLoadBefores(beanWrapper, pluginInstance);
        evictions = evaluatePluginEvictionPolicy(beanWrapper, pluginInstance);
        observedPluginNames = evaluateObservedPlugins(beanWrapper, pluginInstance);
        status = evaluatePluginStatus(beanWrapper, pluginInstance);
        environments = evaluatePluginEnvironments(beanWrapper, pluginInstance);

        return new PluginMetadata(
                pluginName,
                pluginVersion,
                grailsVersion,
                pluginClass,
                loadAfterNames,
                loadBeforeNames,
                dependencies.dependencies,
                dependencies.dependencyNames,
                evictions,
                observedPluginNames,
                environments,
                status != null && status.equalsIgnoreCase(DefaultGrailsPlugin.STATUS_ENABLED)
        );
    }

    private static PluginMetadata extractPluginMetadataFromAnnotation(
            Class<?> pluginClass,
            @Nullable String fallbackVersion,
            @Nullable String fallbackGrailsVersion,
            @Nullable String overrideName) {
        GrailsPluginDescriptor annotation = pluginClass.getAnnotation(GrailsPluginDescriptor.class);
        String pluginName = (overrideName != null && !overrideName.isBlank()) ?
                normalizePluginName(overrideName) :
                getLogicalPluginName(pluginClass);
        String pluginVersion = !annotation.version().isBlank() ? annotation.version().trim() : fallbackVersion;
        if (pluginVersion == null || pluginVersion.isBlank()) {
            throw new PluginException("Plugin [" + pluginName + "] must specify a version!");
        }
        String grailsVersion = !annotation.grailsVersion().isBlank() ?
                annotation.grailsVersion().trim() : fallbackGrailsVersion;

        Map<String, Object> dependencies = new LinkedHashMap<>();
        for (String depSpec : annotation.dependsOn()) {
            if (depSpec == null || depSpec.isBlank()) {
                continue;
            }
            String trimmedSpec = depSpec.trim();
            int sep = trimmedSpec.indexOf(':');
            if (sep >= 0) {
                String rawName = trimmedSpec.substring(0, sep).trim();
                if (rawName.isEmpty()) {
                    continue;
                }
                String depVersion = trimmedSpec.substring(sep + 1).trim();
                dependencies.put(normalizePluginName(rawName), depVersion.isEmpty() ? "*" : depVersion);
            } else {
                dependencies.put(normalizePluginName(trimmedSpec), "*");
            }
        }

        Map<String, Set<Object>> environments = buildEnvironmentsMap(
                Arrays.asList(annotation.environmentsIncludes()),
                Arrays.asList(annotation.environmentsExcludes())
        );

        return new PluginMetadata(
                pluginName,
                pluginVersion,
                grailsVersion,
                pluginClass,
                normalizeNameArray(annotation.loadAfter()),
                normalizeNameArray(annotation.loadBefore()),
                dependencies,
                dependencies.keySet().toArray(new String[0]),
                normalizeNameArray(annotation.evict()),
                normalizeNameArray(annotation.observe()),
                environments,
                annotation.enabled()
        );
    }

    private static @Nullable PluginMetadata extractPluginMetadataFromDescriptor(
            PluginDescriptor descriptor,
            @Nullable Class<?> pluginClass) {
        String pluginName = descriptor.getPluginName();
        if (pluginName == null || pluginName.isBlank()) {
            return null;
        }
        String pluginVersion = descriptor.getPluginVersion();
        if (pluginVersion == null || pluginVersion.isBlank()) {
            throw new PluginException("Plugin [" + pluginName + "] must specify a version!");
        }

        Map<String, Object> dependencies = new LinkedHashMap<>(descriptor.getDependsOn());
        Map<String, Set<Object>> environments = buildEnvironmentsMap(
                descriptor.getEnvironmentsIncludes(),
                descriptor.getEnvironmentsExcludes()
        );

        return new PluginMetadata(
                pluginName,
                pluginVersion,
                descriptor.getGrailsVersionRange(),
                pluginClass,
                normalizeNameArray(descriptor.getLoadAfter().toArray(new String[0])),
                normalizeNameArray(descriptor.getLoadBefore().toArray(new String[0])),
                dependencies,
                dependencies.keySet().toArray(new String[0]),
                normalizeNameArray(descriptor.getEvict().toArray(new String[0])),
                normalizeNameArray(descriptor.getObserve().toArray(new String[0])),
                environments,
                descriptor.isEnabled()
        );
    }

    private static String[] normalizeNameArray(String[] names) {
        List<String> result = new ArrayList<>(names.length);
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                result.add(normalizePluginName(name.trim()));
            }
        }
        return result.toArray(new String[0]);
    }

    private static Map<String, Set<Object>> buildEnvironmentsMap(List<String> includes, List<String> excludes) {
        Map<String, Set<Object>> result = new HashMap<>();
        Function<Object, Object> converter = arg -> {
            var envName = (String) arg;
            var env = Environment.getEnvironment(envName);
            return env != null ? env.getName() : arg;
        };
        if (!includes.isEmpty()) {
            evaluateAndAddListOfValues(result, includes, true, converter);
        }
        if (!excludes.isEmpty()) {
            evaluateAndAddListOfValues(result, excludes, false, converter);
        }
        return result;
    }

    /**
     * Extracts the plugin names observed by the supplied plugin instance.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param pluginInstance the plugin instance being inspected
     * @return the observed plugin names, or an empty array if none are declared
     */
    @Internal
    public static String[] evaluateObservedPlugins(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.OBSERVE)) {
            return new String[0];
        }
        return toStringArray(GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(
                beanWrapper,
                pluginInstance,
                DefaultGrailsPlugin.OBSERVE
        ));
    }

    /**
     * Extracts the environment include/exclude configuration declared by the supplied plugin.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param pluginInstance the plugin instance being inspected
     * @return the environment include/exclude map, or an empty map if none is declared
     */
    @Internal
    public static Map<String, Set<Object>> evaluatePluginEnvironments(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.ENVIRONMENTS)) {
            return new HashMap<>();
        }

        Function<Object, Object> converter = arguments -> {
            var envName = (String) arguments;
            var env = Environment.getEnvironment(envName);
            if (env != null) {
                return env.getName();
            }
            return arguments;
        };
        return evaluateIncludeExcludeProperty(pluginInstance, GrailsPlugin.ENVIRONMENTS, converter);
    }

    /**
     * Extracts the plugin names this plugin prefers to load after.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param pluginInstance the plugin instance being inspected
     * @return the declared load-after plugin names, or an empty array if none are declared
     */
    @Internal
    public static String[] evaluatePluginLoadAfters(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.PLUGIN_LOAD_AFTER_NAMES)) {
            return new String[0];
        }
        return toStringArray(GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(
                beanWrapper,
                pluginInstance,
                GrailsPlugin.PLUGIN_LOAD_AFTER_NAMES
        ));
    }

    /**
     * Extracts the plugin names this plugin prefers to load before.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param pluginInstance the plugin instance being inspected
     * @return the declared load-before plugin names, or an empty array if none are declared
     */
    @Internal
    public static String[] evaluatePluginLoadBefores(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.PLUGIN_LOAD_BEFORE_NAMES)) {
            return new String[0];
        }
        return toStringArray(GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(
                beanWrapper,
                pluginInstance,
                GrailsPlugin.PLUGIN_LOAD_BEFORE_NAMES
        ));
    }

    /**
     * Extracts the plugin names evicted by the supplied plugin.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param pluginInstance the plugin instance being inspected
     * @return the declared evicted plugin names, or an empty array if none are declared
     */
    @Internal
    public static String[] evaluatePluginEvictionPolicy(BeanWrapper beanWrapper, Object pluginInstance) {
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.EVICT)) {
            return new String[0];
        }
        return toStringArray(GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(
                beanWrapper,
                pluginInstance,
                DefaultGrailsPlugin.EVICT
        ));
    }

    /**
     * Extracts the declared plugin version.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param plugin the plugin instance being inspected
     * @param name the logical plugin name used in error messages
     * @return the declared plugin version
     * @throws PluginException if the plugin does not declare a version
     */
    @Internal
    public static String evaluatePluginVersion(BeanWrapper beanWrapper, Object plugin, String name) throws PluginException {
        return evaluatePluginVersion(beanWrapper, plugin, name, null);
    }

    @Internal
    public static String evaluatePluginVersion(
            BeanWrapper beanWrapper,
            Object plugin,
            String name,
            @Nullable String fallbackVersion) throws PluginException {
        if (beanWrapper.isReadableProperty(DefaultGrailsPlugin.VERSION)) {
            var version = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, DefaultGrailsPlugin.VERSION);
            if (version != null) {
                return version.toString();
            }
        }
        if (fallbackVersion != null && !fallbackVersion.isBlank()) {
            return fallbackVersion;
        }
        throw new PluginException("Plugin [" + name + "] must specify a version. e.g.: def version = '0.1'");
    }

    /**
     * Determines whether the supplied plugin is enabled.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param plugin the plugin instance being inspected
     * @return the normalized plugin status string
     */
    @Internal
    public static String evaluatePluginStatus(BeanWrapper beanWrapper, Object plugin) {
        if (plugin instanceof Plugin) {
            return ((Plugin) plugin).enabled ? DefaultGrailsPlugin.STATUS_ENABLED : DefaultGrailsPlugin.STATUS_DISABLED;
        }

        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.STATUS)) {
            return DefaultGrailsPlugin.STATUS_ENABLED;
        }

        var statusObj = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, DefaultGrailsPlugin.STATUS);
        if (statusObj != null) {
            return statusObj.toString().toLowerCase();
        }

        return DefaultGrailsPlugin.STATUS_ENABLED;
    }

    /**
     * Holds both the dependency names and the raw dependency map extracted from a plugin.
     */
    @Internal
    public record PluginDependencies(String[] dependencyNames, Map<String, Object> dependencies) {

    }

    /**
     * Extracts the declared plugin dependencies.
     *
     * @param beanWrapper the wrapper used to inspect plugin properties
     * @param plugin the plugin instance being inspected
     * @return the dependency names together with the raw dependency map
     */
    @Internal
    public static PluginDependencies evaluatePluginDependencies(BeanWrapper beanWrapper, Object plugin) {
        if (!beanWrapper.isReadableProperty(GrailsPlugin.DEPENDS_ON)) {
            return emptyPluginDependencies();
        }
        try {
            Object value = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(
                    beanWrapper,
                    plugin,
                    GrailsPlugin.DEPENDS_ON
            );
            if (!(value instanceof Map)) {
                return emptyPluginDependencies();
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> dependencies = (Map<String, Object>) value;
            return new PluginDependencies(
                    dependencies.keySet().toArray(new String[0]),
                    dependencies
            );
        } catch (Exception e) {
            LOG.trace("Could not read property [dependsOn]: {}", e.getMessage());
            return emptyPluginDependencies();
        }
    }

    /**
     * Resolves the plugin configuration resource for the supplied plugin class.
     *
     * <p>Looks up the namespaced location {@code META-INF/grails/plugins/<name>/plugin.yml} or
     * {@code plugin.groovy} first, then any AOT-generated copy, and finally falls back to probing
     * {@code /plugin.yml} or {@code /plugin.groovy} relative to the plugin class root. If both
     * YAML and Groovy configuration files exist, a {@link RuntimeException} is thrown.</p>
     *
     * @param pluginClass the plugin class to resolve configuration for
     * @return the configuration resource, or {@code null} if no config file is present
     */
    public static @Nullable Resource readPluginConfiguration(Class<?> pluginClass) {
        return readPluginConfiguration(getLogicalPluginName(pluginClass), pluginClass, pluginClass.getClassLoader());
    }

    /**
     * Resolves the plugin configuration resource for a plugin by logical name and optional plugin class.
     *
     * @param pluginName the logical plugin name (may be {@code null} if only {@code pluginClass} is provided)
     * @param pluginClass the plugin class, or {@code null} for descriptor-less starter plugins
     * @param classLoader the class loader to search for namespaced plugin configuration
     * @return the configuration resource, or {@code null} if no config file is present
     * @since 9.0
     */
    public static @Nullable Resource readPluginConfiguration(
            @Nullable String pluginName,
            @Nullable Class<?> pluginClass,
            @Nullable ClassLoader classLoader) {
        String effectiveName = pluginName != null ? normalizePluginName(pluginName) :
                (pluginClass != null ? getLogicalPluginName(pluginClass) : null);
        ClassLoader loader = classLoader != null ? classLoader :
                (pluginClass != null ? pluginClass.getClassLoader() : Thread.currentThread().getContextClassLoader());

        if (effectiveName != null && isValidPluginName(effectiveName) && loader != null) {
            URL namespacedYml = loader.getResource(getPluginConfigPath(effectiveName, PLUGIN_YML_CONFIG));
            URL namespacedGroovy = loader.getResource(getPluginConfigPath(effectiveName, PLUGIN_GROOVY_CONFIG));
            if (namespacedYml != null && namespacedGroovy != null) {
                throw new RuntimeException("A plugin [" + effectiveName +
                        "] may define a plugin.yml or a plugin.groovy, but not both");
            }
            if (namespacedYml != null) {
                return new UrlResource(namespacedYml);
            }
            if (namespacedGroovy != null) {
                return new UrlResource(namespacedGroovy);
            }
        }

        if (pluginClass == null) {
            return null;
        }

        Resource generatedResource = findGeneratedConfiguration(pluginClass, loader);
        if (generatedResource != null) {
            return generatedResource;
        }

        var ymlResource = getConfigurationResource(pluginClass, PLUGIN_YML_CONFIG_PATH);
        var groovyResource = getConfigurationResource(pluginClass, PLUGIN_GROOVY_CONFIG_PATH);

        boolean groovyResourceExists = groovyResource != null && groovyResource.exists();

        if (ymlResource != null && ymlResource.exists()) {
            if (groovyResourceExists) {
                throw new RuntimeException("A plugin [" + pluginClass.getName() +
                        "] may define a plugin.yml or a plugin.groovy, but not both");
            }
            return ymlResource;
        }
        if (groovyResourceExists) {
            return groovyResource;
        }
        return null;
    }

    /**
     * The classpath location of the copy the build makes of a plugin's configuration while generating code
     * ahead of time for legacy root-packaged configurations.
     *
     * @param pluginClassName the fully qualified name of the plugin class
     * @param configFileName the name of the configuration file, {@code plugin.yml} or {@code plugin.groovy}
     * @return the location of the copy, relative to the root of the classpath
     * @since 9.0
     */
    public static String getGeneratedConfigurationPath(String pluginClassName, String configFileName) {
        return GENERATED_CONFIG_PATH + pluginClassName + "/" + configFileName;
    }

    private static @Nullable Resource findGeneratedConfiguration(Class<?> pluginClass, @Nullable ClassLoader loader) {
        ClassLoader classLoader = loader != null ? loader : pluginClass.getClassLoader();
        if (classLoader == null) {
            return null;
        }
        for (String configFileName : List.of(PLUGIN_YML_CONFIG, PLUGIN_GROOVY_CONFIG)) {
            URL generated = classLoader.getResource(getGeneratedConfigurationPath(pluginClass.getName(), configFileName));
            if (generated != null) {
                return new UrlResource(generated);
            }
        }
        return null;
    }

    /**
     * Finds a plugin configuration resource at the given path relative to the
     * plugin class, delegating to {@link IOUtils#findResourceRelativeToClass}.
     *
     * @param pluginClass the plugin class to resolve relative to
     * @param configPath the path to probe (for example {@code "/plugin.yml"})
     * @return the resource wrapping the configuration URL, or {@code null} if no such resource exists
     */
    public static @Nullable Resource getConfigurationResource(Class<?> pluginClass, String configPath) {
        URL urlToConfig = IOUtils.findResourceRelativeToClass(pluginClass, configPath);
        return urlToConfig != null ? new UrlResource(urlToConfig) : null;
    }

    /**
     * Determines whether the supplied class is a valid Grails plugin descriptor class, either by
     * following the {@code *GrailsPlugin} naming convention or by carrying {@link GrailsPluginDescriptor}.
     *
     * @param pluginClass the class to inspect
     * @return {@code true} if the class is a valid Grails plugin class
     */
    public static boolean isGrailsPluginClassNamedCorrectly(@Nullable Class<?> pluginClass) {
        return pluginClass != null &&
                (pluginClass.getName().endsWith(GRAILS_PLUGIN_SUFFIX) ||
                        pluginClass.isAnnotationPresent(GrailsPluginDescriptor.class));
    }

    /**
     * Determines whether the supplied class can be loaded as a Grails plugin.
     *
     * @param pluginClass the class to inspect
     * @return {@code true} if the class is non-null, non-abstract, and not the framework base plugin class
     */
    public static boolean isGrailsPluginLoadable(@Nullable Class<?> pluginClass) {
        return pluginClass != null &&
                !Modifier.isAbstract(pluginClass.getModifiers()) &&
                !GrailsPlugin.class.isAssignableFrom(pluginClass);
    }

    /**
     * Determines whether a plugin's declared Grails version range is compatible with the target Grails version.
     *
     * @param pluginVersion the plugin version used in log messages
     * @param pluginSupportedVersionRange the Grails version or version range declared by the plugin
     * @param grailsVersion the target Grails version to test against
     * @param pluginDescription the plugin description used in log messages
     * @return {@code true} if the plugin should be considered compatible with the supplied Grails version
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

    /**
     * Evaluates an include/exclude style plugin property into a normalized lookup map.
     *
     * @param pluginBean the plugin bean containing the property
     * @param name the property name to evaluate
     * @param converter a converter applied to each include/exclude value before storage
     * @return the normalized include/exclude map
     */
    public static Map<String, Set<Object>> evaluateIncludeExcludeProperty(
            Object pluginBean,
            String name,
            Function<Object, Object> converter
    ) {
        var resultMap = new HashMap<String, Set<Object>>();
        var propertyValue = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(pluginBean, name);
        if (propertyValue instanceof Map) {
            var containedMap = (Map) propertyValue;

            var includes = containedMap.get(DefaultGrailsPlugin.INCLUDES);
            evaluateAndAddIncludeExcludeObject(resultMap, includes, true, converter);

            var excludes = containedMap.get(DefaultGrailsPlugin.EXCLUDES);
            evaluateAndAddIncludeExcludeObject(resultMap, excludes, false, converter);
        } else {
            evaluateAndAddIncludeExcludeObject(resultMap, propertyValue, true, converter);
        }
        return resultMap;
    }

    /**
     * Determines whether a value is permitted by a normalized include/exclude map.
     *
     * @param includeExcludeMap the include/exclude map to evaluate
     * @param value the value to test
     * @return {@code true} if the value is permitted by the include/exclude rules
     */
    public static boolean supportsValueInIncludeExcludeMap(Map<String, Set<Object>> includeExcludeMap, Object value) {
        if (includeExcludeMap.isEmpty()) {
            return true;
        }
        var includes = includeExcludeMap.get(DefaultGrailsPlugin.INCLUDES);
        if (includes != null) {
            return includes.contains(value);
        }
        var excludes = includeExcludeMap.get(DefaultGrailsPlugin.EXCLUDES);
        return !(excludes != null && excludes.contains(value));
    }

    private static @Nullable String getPluginGrailsVersionRange(Object pluginInstance, String pluginName) {
        try {
            var grailsVersionRange = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(pluginInstance, PLUGIN_GRAILS_VERSION_FIELD);
            return grailsVersionRange != null ? grailsVersionRange.toString() : null;
        } catch (Exception e) {
            LOG.warn("Could not determine Grails Plugin compatible version range for plugin [{}], assuming compatible", pluginName);
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
            return null;
        }
    }

    private static void evaluateAndAddIncludeExcludeObject(
            Map<String, Set<Object>> targetMap,
            Object includeExcludeObject,
            boolean include,
            Function<Object, Object> converter
    ) {
        if (includeExcludeObject instanceof String) {
            evaluateAndAddToIncludeExcludeSet(targetMap, (String) includeExcludeObject, include, converter);
        } else if (includeExcludeObject instanceof List) {
            evaluateAndAddListOfValues(targetMap, (List) includeExcludeObject, include, converter);
        }
    }

    private static void evaluateAndAddListOfValues(
            Map<String, Set<Object>> targetMap,
            List includeExcludeList,
            boolean include,
            Function<Object, Object> converter
    ) {
        for (var value : includeExcludeList) {
            if (value instanceof String) {
                evaluateAndAddToIncludeExcludeSet(targetMap, (String) value, include, converter);
            }
        }
    }

    private static void evaluateAndAddToIncludeExcludeSet(
            Map<String, Set<Object>> targetMap,
            String includeExcludeString,
            boolean include,
            Function<Object, Object> converter
    ) {
        var set = lazilyCreateIncludeOrExcludeSet(targetMap, include);
        set.add(converter.apply(includeExcludeString));
    }

    private static Set<Object> lazilyCreateIncludeOrExcludeSet(Map<String, Set<Object>> targetMap, boolean include) {
        var key = include ? DefaultGrailsPlugin.INCLUDES : DefaultGrailsPlugin.EXCLUDES;
        return targetMap.computeIfAbsent(key, k -> new HashSet<>());
    }

    private static String[] toStringArray(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(o -> o == null ? "" : o.toString()).toArray(String[]::new);
        }
        if (value instanceof String[] array) {
            return array;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(o -> o == null ? "" : o.toString()).toArray(String[]::new);
        }
        if (value instanceof String s) {
            return new String[]{s};
        }
        return new String[0];
    }

    private static PluginDependencies emptyPluginDependencies() {
        return new PluginDependencies(new String[0], new HashMap<>());
    }
}
