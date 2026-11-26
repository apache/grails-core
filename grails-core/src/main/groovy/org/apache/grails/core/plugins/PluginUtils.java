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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;

import groovy.transform.Internal;

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
import grails.util.Environment;
import grails.util.GrailsClassUtils;
import grails.util.GrailsNameUtils;
import org.grails.io.support.SpringIOUtils;
import org.grails.plugins.DefaultGrailsPlugin;

/**
 * Static utility methods supporting Grails plugin discovery, metadata extraction, configuration lookup,
 * and compatibility checks.
 *
 * <p>This class centralizes the low-level operations used by bootstrap-time discovery, including scanning
 * {@code grails-plugin.xml} descriptors, deriving logical plugin names, reading plugin class metadata, and
 * evaluating environment and version constraints.</p>
 */
public final class PluginUtils {

    private static final Logger LOG = LoggerFactory.getLogger(PluginUtils.class);

    /**
     * The wildcard pattern used to indicate that a plugin observes all other plugins,
     * i.e., it should be notified of events from all plugins regardless of name.
     */
    public static final String WILDCARD_OBSERVER_PATTERN = "*";

    /**
     * The classpath location of the Grails plugin descriptor XML files.
     */
    public static final String PLUGIN_XML_PATTERN = "META-INF/grails-plugin.xml";

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
     * The naming suffix required for Grails plugin implementation classes.
     */
    public static final String GRAILS_PLUGIN_SUFFIX = "GrailsPlugin";

    private PluginUtils() {
        // prevent instantiation - all methods are static
    }

    /**
     * Scans all {@code META-INF/grails-plugin.xml} resources visible to the supplied class loader.
     *
     * <p>Each descriptor is parsed with {@link PluginXmlHandler} and returned as a {@link PluginDescriptor}
     * containing the descriptor resource, declared plugin implementation classes, and any provided class names.
     * Invalid descriptors are logged and skipped, so discovery can continue.</p>
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of {@link PluginDescriptor} records, one per
     *         {@code META-INF/grails-plugin.xml} resource found on the
     *         classpath
     */
    public static List<PluginDescriptor> scanPluginDescriptorResources(ClassLoader classLoader) {
        List<PluginDescriptor> descriptors = new ArrayList<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(PLUGIN_XML_PATTERN);
            SAXParser saxParser = SpringIOUtils.newSAXParser();

            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try (InputStream input = url.openStream()) {
                    PluginXmlHandler handler = new PluginXmlHandler();
                    saxParser.parse(input, handler);
                    Resource xmlResource = new UrlResource(url);
                    descriptors.add(new PluginDescriptor(
                            xmlResource,
                            handler.getPluginClassNames(),
                            handler.getProvidedClasses()
                    ));
                } catch (IOException | SAXException e) {
                    LOG.debug("Error parsing plugin descriptor at [{}]: {}", url, e.getMessage());
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOG.debug("Error scanning for plugin descriptors: {}", e.getMessage());
        }

        return descriptors;
    }

    /**
     * Creates a {@link PluginInfo} for a single plugin class using a synthetic descriptor.
     *
     * @param pluginClass the plugin implementation class
     * @param descriptorResource the descriptor resource associated with the plugin, if any
     * @param dynamic whether the plugin originated from a dynamic plugin configuration
     * @return the created plugin info
     */
    public static PluginInfo createPluginInfo(Class<?> pluginClass, Resource descriptorResource, boolean dynamic) {
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
        PluginMetadata metadata = PluginUtils.extractPluginMetadata(pluginClass);
        Resource configResource = PluginUtils.readPluginConfiguration(pluginClass);
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
     * Convenience method that scans all {@code META-INF/grails-plugin.xml}
     * resources and returns just the plugin class names.
     *
     * @param classLoader the class loader to scan for plugin descriptors
     * @return a list of fully qualified plugin class names discovered from
     *         {@code META-INF/grails-plugin.xml} descriptors
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
     * Derives the logical plugin name from the plugin class, following Grails
     * conventions.
     *
     * <p>For example, {@code org.grails.plugins.CoreGrailsPlugin} becomes
     * {@code core}. This delegates to
     * {@link GrailsNameUtils#getLogicalPropertyName} with the
     * {@code "GrailsPlugin"} suffix.</p>
     *
     * @param pluginClass the plugin class
     * @return the logical plugin name
     */
    public static String getLogicalPluginName(Class<?> pluginClass) {
        return getLogicalPluginNameFromClassName(pluginClass.getSimpleName());
    }

    /**
     * Derives the logical plugin name from a plugin class name.
     *
     * @param name the plugin class simple name
     * @return the logical plugin name with the {@link #GRAILS_PLUGIN_SUFFIX} removed
     */
    public static String getLogicalPluginNameFromClassName(String name) {
        return GrailsNameUtils.getLogicalPropertyName(name, GRAILS_PLUGIN_SUFFIX);
    }

    /**
     * Extracts plugin metadata ({@code loadAfter}, {@code loadBefore},
     * {@code dependsOn}) from a plugin class without requiring a full
     * {@link grails.core.GrailsApplication} or plugin manager.
     *
     * <p>The plugin class is instantiated to read its properties via a
     * {@link BeanWrapper}, mirroring the approach used by
     * {@link DefaultGrailsPlugin}. The {@code dependsOn} property is a
     * {@code Map<String, String>} where keys are dependency plugin names and
     * values are version constraints; only the keys are extracted.</p>
     *
     * @param pluginClass the plugin class to extract metadata from
     * @return a {@link PluginMetadata} instance, or {@code null} if the class is
     *         not a valid Grails plugin
     */
    public static PluginMetadata extractPluginMetadata(Class<?> pluginClass) {
        if (!isGrailsPluginClassNamedCorrectly(pluginClass)) {
            return null;
        }

        String pluginName = getLogicalPluginName(pluginClass);
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

        pluginVersion = evaluatePluginVersion(beanWrapper, pluginInstance, pluginName);
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
            if (env != null) return env.getName();
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
        if (!beanWrapper.isReadableProperty(DefaultGrailsPlugin.VERSION)) {
            throw new PluginException("Plugin [" + name + "] must specify a version!");
        }

        var version = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, DefaultGrailsPlugin.VERSION);
        if (version == null) {
            throw new PluginException("Plugin [" + name + "] must specify a version. e.g.: def version = '0.1'");
        }

        return version.toString();
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
        }
        catch (Exception e) {
            LOG.trace("Could not read property [dependsOn]: {}", e.getMessage());
            return emptyPluginDependencies();
        }
    }

    /**
     * Resolves the plugin configuration resource relative to the supplied plugin class.
     *
     * <p>The lookup probes for both {@code plugin.yml} and {@code plugin.groovy}. If both exist, a
     * {@link RuntimeException} is thrown because a plugin may define only one configuration source.</p>
     *
     * @param pluginClass the plugin class to resolve relative to
     * @return the configuration resource, or {@code null} if no config file is present
     */
    public static Resource readPluginConfiguration(Class<?> pluginClass) {
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
     * Finds a plugin configuration resource at the given path relative to the
     * plugin class, delegating to
     * {@link IOUtils#findResourceRelativeToClass}.
     *
     * @param pluginClass the plugin class to resolve relative to
     * @param configPath the path to probe (for example {@code "/plugin.yml"})
     * @return the resource wrapping the configuration URL, or {@code null} if no such resource exists
     */
    public static Resource getConfigurationResource(Class<?> pluginClass, String configPath) {
        URL urlToConfig = IOUtils.findResourceRelativeToClass(pluginClass, configPath);
        return urlToConfig != null ? new UrlResource(urlToConfig) : null;
    }

    /**
     * Determines whether the supplied class follows Grails plugin naming conventions.
     *
     * @param pluginClass the class to inspect
     * @return {@code true} if the class name ends with {@link #GRAILS_PLUGIN_SUFFIX}
     */
    public static boolean isGrailsPluginClassNamedCorrectly(Class<?> pluginClass) {
        return pluginClass != null && pluginClass.getName().endsWith(GRAILS_PLUGIN_SUFFIX);
    }

    /**
     * Determines whether the supplied class can be loaded as a Grails plugin.
     *
     * @param pluginClass the class to inspect
     * @return {@code true} if the class is non-null, non-abstract, and not the framework base plugin class
     */
    public static boolean isGrailsPluginLoadable(Class<?> pluginClass) {
        return pluginClass != null && !Modifier.isAbstract(pluginClass.getModifiers()) && pluginClass != DefaultGrailsPlugin.class;
    }

    /**
     * Determines whether a plugin's declared Grails version range is compatible with the target Grails version.
     *
     * <p>This method supports exact version checks and version ranges as interpreted by
     * {@link GrailsVersionUtils}. Compatibility problems are logged and reported by returning {@code false}.</p>
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
            //exact version compatibility required
            if (!grailsVersion.equals(pluginMinGrailsVersion)) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is not equal" +
                                " to the one that plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }
        }
        if (!pluginMaxGrailsVersion.equals("*")) {
            // Case 1: a max version not specified. Forward compatibility expected

            // the minimum version required by the plugin cannot be greater than the grails app version
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }
        } else {
            // Case 2: both max and min version specified. Strict compatibility expected

            // the minimum version required by the plugin cannot be greater than the grails app version
            if (comparator.compare(pluginMinGrailsVersion, grailsVersion) > 0) {
                LOG.warn("Plugin [{}:{}] may not be compatible with this application as the application Grails version is less" +
                                " than the plugin requires. Plugin is compatible with Grails version {} but app is {}",
                        pluginDescription, pluginVersion, pluginSupportedVersionRange, grailsVersion);
                return false;
            }

            // the maximum version required by the plugin cannot be less than the grails app version
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
     * <p>The property may be declared either as a map containing {@code includes} and {@code excludes} entries,
     * or as a single include value. Values are converted through the supplied converter before being stored.</p>
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
     * <p>An empty map allows every value. If an include set is present, only included values are supported.
     * Otherwise, values are rejected only when they appear in the exclude set.</p>
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

    /**
     * Resolves the declared Grails version range supported by the supplied plugin.
     *
     * @param pluginInstance the plugin instance being inspected
     * @param pluginName the logical plugin name used in log messages
     * @return the declared Grails version range, or {@code null} if it cannot be determined
     */
    private static String getPluginGrailsVersionRange(Object pluginInstance, String pluginName) {
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
            Map targetMap,
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
        if (!(value instanceof Collection)) {
            return new String[0];
        }
        Collection<?> collection = (Collection<?>) value;
        return collection.stream()
                .map(o -> o == null ? "" : o.toString())
                .toArray(String[]::new);
    }

    private static PluginDependencies emptyPluginDependencies() {
        return new PluginDependencies(new String[0], new HashMap<>());
    }
}
