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
package org.grails.plugins;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import grails.core.GrailsApplication;
import grails.io.IOUtils;
import grails.io.ResourceUtils;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.Plugin;
import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.PluginDefinition;
import grails.util.BuildSettings;
import grails.util.CollectionUtils;
import grails.util.Environment;
import grails.util.GrailsNameUtils;
import org.apache.grails.core.plugins.PluginInfo;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.core.io.StaticResourceLoader;
import org.grails.plugins.support.WatchPattern;
import org.grails.plugins.support.WatchPatternParser;

/**
 * A Grails plugin: an instance of the plugin's {@link Plugin} subclass, described by its {@link PluginInfo}.
 *
 * <p>Everything known before the plugin runs, its name, version, ordering, environments, artefacts and watched
 * resources, comes from its {@link PluginDefinition}; its behaviour is its overridden {@code Plugin} methods.
 * What else a plugin offers depends on what it has, not on where it was found:</p>
 * <ul>
 *     <li>its artefacts, every class its definition lists, loaded with the application's class loader, or else
 *     the plugin class's own;</li>
 *     <li>its precompiled GSP views, from {@code META-INF/grails/plugins/<name>/views.properties} when the
 *     class path has one;</li>
 *     <li>its static resources, message bundles and project directory, from the location its class was loaded
 *     from: a jar, or a project's class directory, in which case the plugin is the base plugin when that project is
 *     the one being run.</li>
 * </ul>
 * <p>A plugin class registered directly, compiled in memory, simply has fewer of these.</p>
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@SuppressWarnings("rawtypes")
public class DefaultGrailsPlugin implements GrailsPlugin {

    public static final String INCLUDES = "includes";
    public static final String EXCLUDES = "excludes";

    public static final char UNDERSCORE = '_';
    public static final String PROPERTIES_EXTENSION = ".properties";
    public static final String DEFAULT_PROPERTIES_ENCODING = "UTF-8";

    protected static final Log LOG = LogFactory.getLog(DefaultGrailsPlugin.class);

    private static final String PLUGIN_CHANGE_EVENT_CTX = "ctx";
    private static final String PLUGIN_CHANGE_EVENT_APPLICATION = "application";
    private static final String PLUGIN_CHANGE_EVENT_PLUGIN = "plugin";
    private static final String PLUGIN_CHANGE_EVENT_SOURCE = "source";
    private static final String PLUGIN_CHANGE_EVENT_MANAGER = "manager";

    private final PluginInfo pluginInfo;
    private final PluginDefinition definition;
    private final Plugin plugin;
    private final Class<?>[] providedArtefacts;
    private final List<WatchPattern> watchedResourcePatterns;
    private final Map<String, Class<?>> precompiledViewMap = new HashMap<>();
    private final @Nullable Resource baseResource;
    private final @Nullable Resource baseResourcesResource;
    private final @Nullable File projectDirectory;
    private final boolean basePlugin;

    private GrailsApplication grailsApplication;
    private ApplicationContext applicationContext;
    private GrailsPluginManager manager;

    /**
     * A plugin class registered directly, described by its {@code @GrailsPluginDescriptor}.
     *
     * @param pluginClass a concrete {@link Plugin} subclass annotated with {@code @GrailsPluginDescriptor}
     * @param application the Grails application
     * @throws PluginException if the class is not a Grails plugin
     */
    public DefaultGrailsPlugin(Class<?> pluginClass, GrailsApplication application) {
        this(PluginUtils.createPluginInfo(pluginClass), application);
    }

    /**
     * A discovered plugin.
     *
     * @param pluginInfo the plugin, as discovery found it
     * @param application the Grails application
     * @throws PluginException if the plugin cannot be created or its definition names classes that cannot be loaded
     */
    public DefaultGrailsPlugin(PluginInfo pluginInfo, GrailsApplication application) {
        Assert.notNull(pluginInfo, "Argument [pluginInfo] cannot be null");
        this.pluginInfo = pluginInfo;
        this.definition = pluginInfo.getDefinition();
        this.grailsApplication = application;
        Class<? extends Plugin> pluginClass = pluginInfo.getPluginClass();
        try {
            this.plugin = instantiate(pluginClass);
            plugin.setPlugin(this);
            plugin.setGrailsApplication(application);
            this.providedArtefacts = loadClasses(definition.getProvidedClasses());
            this.watchedResourcePatterns = evaluateWatchedResources();
        }
        catch (PluginException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new PluginException("Error initialising plugin for class [" + pluginClass.getName() + "]: " + e.getMessage(), e);
        }

        URL rootResource = IOUtils.findClassResource(pluginClass) != null ? IOUtils.findRootResource(pluginClass) : null;
        this.baseResource = rootResource != null ? new UrlResource(rootResource) : null;
        this.projectDirectory = rootResource != null && !isJar(baseResource) ? IOUtils.findApplicationDirectoryFile(pluginClass) : null;
        this.basePlugin = isInBaseDirectory(projectDirectory);
        URL rootResourcesUrl = rootResource != null ? IOUtils.findRootResourcesURL(pluginClass) : null;
        this.baseResourcesResource = rootResourcesUrl != null ? new UrlResource(rootResourcesUrl) : null;
        initializeViewMap();
    }

    private static Plugin instantiate(Class<? extends Plugin> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        }
        catch (InvocationTargetException e) {
            throw new PluginException("Plugin [" + clazz.getName() + "] could not be created: " + e.getCause().getMessage(), e.getCause());
        }
        catch (ReflectiveOperationException e) {
            throw new PluginException("Plugin [" + clazz.getName() + "] must have a public no-argument constructor", e);
        }
    }

    private static boolean isJar(Resource baseResource) {
        try {
            return ResourceUtils.isJarURL(baseResource.getURL());
        }
        catch (IOException e) {
            return true;
        }
    }

    private static boolean isInBaseDirectory(@Nullable File projectDirectory) {
        if (BuildSettings.BASE_DIR == null || projectDirectory == null) {
            return false;
        }
        try {
            return projectDirectory.getCanonicalPath().startsWith(BuildSettings.BASE_DIR.getCanonicalPath());
        }
        catch (IOException e) {
            return false;
        }
    }

    /**
     * Loads the classes a definition names: with the application's class loader, so a class reloaded in
     * development is the application's copy, or else with the plugin class's own, which sees every class its
     * plugin names.
     */
    private Class<?>[] loadClasses(List<String> classNames) {
        ClassLoader applicationLoader = grailsApplication != null ? grailsApplication.getClassLoader() : null;
        List<Class<?>> classes = new ArrayList<>();
        for (String className : classNames) {
            classes.add(loadClass(className, applicationLoader));
        }
        return classes.toArray(new Class<?>[0]);
    }

    private Class<?> loadClass(String className, @Nullable ClassLoader applicationLoader) {
        if (applicationLoader != null) {
            try {
                return applicationLoader.loadClass(className);
            }
            catch (ClassNotFoundException e) {
                // the plugin's own class loader may still see it
            }
        }
        try {
            return Class.forName(className, false, pluginInfo.getPluginClass().getClassLoader());
        }
        catch (ClassNotFoundException | LinkageError e) {
            throw new PluginException("Failed to load class [" + className + "] of plugin [" + getName() + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Reads the plugin's precompiled views from {@code META-INF/grails/plugins/<name>/views.properties}.
     */
    private void initializeViewMap() {
        URL manifest = grailsApplication != null ?
                grailsApplication.getClassLoader().getResource(PluginUtils.getPluginViewsPath(getName())) : null;
        if (manifest == null) {
            return;
        }
        Properties viewsProperties = new Properties();
        try (InputStream input = manifest.openStream()) {
            viewsProperties.load(input);
        }
        catch (IOException e) {
            throw new PluginException("Could not read the views of plugin [" + getName() + "] from " + manifest, e);
        }
        for (String viewName : viewsProperties.stringPropertyNames()) {
            String viewClassName = viewsProperties.getProperty(viewName);
            try {
                precompiledViewMap.put(viewName, grailsApplication.getClassLoader().loadClass(viewClassName));
            }
            catch (ClassNotFoundException | LinkageError e) {
                throw new PluginException(
                        "Failed to initialize view [" + viewName + "] from plugin [" + getName() + "] : " + e.getMessage(), e);
            }
        }
    }

    private List<WatchPattern> evaluateWatchedResources() {
        Environment env = Environment.getCurrent();
        List<String> patterns = definition.getWatchedResources();
        if ((env.isWarDeployed() && !env.isReloadEnabled()) || patterns.isEmpty()) {
            return new ArrayList<>();
        }
        String baseLocation = env.getReloadLocation();
        List<String> references = new ArrayList<>();
        for (String pattern : patterns) {
            references.add(baseLocation == null ? pattern : getResourcePatternForBaseLocation(baseLocation, pattern));
        }
        try {
            return new WatchPatternParser().getWatchPatterns(references);
        }
        catch (IllegalArgumentException e) {
            throw new PluginException("Plugin [" + getName() + "] declares invalid watched resources " + references + ": " + e.getMessage(), e);
        }
    }

    private static String getResourcePatternForBaseLocation(String baseLocation, String resourcePath) {
        String location = baseLocation;
        if (!location.endsWith(File.separator)) {
            location = location + File.separator;
        }
        if (resourcePath.startsWith("./")) {
            return "file:" + location + resourcePath.substring(2);
        }
        else if (resourcePath.startsWith("file:./")) {
            return "file:" + location + resourcePath.substring(7);
        }
        return resourcePath;
    }

    /** @return the plugin, as discovery found it */
    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }

    /** @return the plugin's definition */
    public PluginDefinition getDefinition() {
        return definition;
    }

    @Override
    public String getName() {
        return definition.getPluginName();
    }

    @Override
    public String getVersion() {
        return definition.getPluginVersion();
    }

    @Override
    public String getFullName() {
        return getName() + versionSuffix();
    }

    /**
     * {@code -<version>}, or nothing for a plugin without a version, one compiled outside a build that sets it,
     * so names and paths built from a plugin's version never end in {@code -null}.
     */
    private String versionSuffix() {
        String version = getVersion();
        return version != null ? "-" + version : "";
    }

    @Override
    public String getFileSystemName() {
        return getFileSystemShortName() + versionSuffix();
    }

    @Override
    public String getFileSystemShortName() {
        return GrailsNameUtils.getScriptName(getName());
    }

    @Override
    public String getPluginPath() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getScriptName(getName()) + versionSuffix();
    }

    // https://github.com/apache/grails-core/issues/9406
    // The name of the plugin for my-plug on the path is myPlugin the GrailsNameUtils.getScriptName(getName()) will always use my-plugin
    @Override
    public String getPluginPathCamelCase() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(getName()) + versionSuffix();
    }

    @Override
    public Class<? extends Plugin> getPluginClass() {
        return pluginInfo.getPluginClass();
    }

    @Override
    public Plugin getPluginInstance() {
        return plugin;
    }

    @Override
    public boolean isBasePlugin() {
        return basePlugin;
    }

    /**
     * @return the directory of the project whose class directory the plugin was loaded from, or {@code null} for a
     *         plugin loaded from a jar or compiled in memory
     */
    public @Nullable File getProjectDirectory() {
        return projectDirectory;
    }

    @Override
    public GrailsPluginManager getManager() {
        return manager;
    }

    @Override
    public void setManager(GrailsPluginManager manager) {
        this.manager = manager;
        plugin.setPluginManager(manager);
    }

    @Override
    public void setApplication(GrailsApplication application) {
        this.grailsApplication = application;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        plugin.setApplicationContext(applicationContext);
        if (plugin instanceof ApplicationListener listener && applicationContext instanceof ConfigurableApplicationContext context) {
            context.addApplicationListener(listener);
        }
    }

    @Override
    public String[] getAfterNames() {
        return pluginInfo.getAfterNames();
    }

    @Override
    public String[] getBeforeNames() {
        return pluginInfo.getBeforeNames();
    }

    @Override
    public String[] getDependencyNames() {
        return pluginInfo.getDependsOnNames();
    }

    @Override
    public String getDependentVersion(String name) {
        return pluginInfo.getDependentVersion(name);
    }

    @Override
    public String[] getObservedPluginNames() {
        return pluginInfo.getObservedPluginNames();
    }

    @Override
    public Class<?>[] getProvidedArtefacts() {
        return providedArtefacts;
    }

    @Override
    public List<WatchPattern> getWatchedResourcePatterns() {
        return watchedResourcePatterns;
    }

    @Override
    public boolean hasInterestInChange(String path) {
        for (WatchPattern watchedResourcePattern : watchedResourcePatterns) {
            if (watchedResourcePattern.matchesPath(path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean supportsEnvironment(Environment environment) {
        return pluginInfo.supportsEnvironment(environment);
    }

    @Override
    public boolean supportsCurrentScopeAndEnvironment() {
        return supportsEnvironment(Environment.getCurrent());
    }

    @Override
    public void doWithApplicationContext(ApplicationContext ctx) {
        plugin.setApplicationContext(ctx);
        plugin.doWithApplicationContext();
    }

    @Override
    public BeanRegistrar getBeanRegistrar() {
        return plugin.beanRegistrar();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void notifyOfEvent(Map event) {
        plugin.onChange(event);
    }

    @Override
    public Map notifyOfEvent(int eventKind, final Object source) {
        @SuppressWarnings("unchecked")
        Map<String, Object> event = CollectionUtils.<String, Object>newMap(
            PLUGIN_CHANGE_EVENT_SOURCE, source,
            PLUGIN_CHANGE_EVENT_PLUGIN, plugin,
            PLUGIN_CHANGE_EVENT_APPLICATION, grailsApplication,
            PLUGIN_CHANGE_EVENT_MANAGER, getManager(),
            PLUGIN_CHANGE_EVENT_CTX, applicationContext);

        switch (eventKind) {
            case EVENT_ON_CHANGE:
                notifyOfEvent(event);
                getManager().informObservers(getName(), event);
                break;
            case EVENT_ON_SHUTDOWN:
                plugin.onShutdown(event);
                break;
            case EVENT_ON_CONFIG_CHANGE:
                plugin.onConfigChange(event);
                break;
            default:
                notifyOfEvent(event);
        }
        return event;
    }

    /**
     * Resolves one of the plugin's precompiled GSP views.
     *
     * @param viewName the view name, with or without the plugin's path
     * @return the view class, a subclass of {@code GroovyPage}, or {@code null} if the plugin has no such view
     */
    public @Nullable Class<?> resolveView(String viewName) {
        String extraPath = "/plugins/" + getName() + versionSuffix() + '/';
        return precompiledViewMap.get(viewName.replace(extraPath, "/"));
    }

    /**
     * Resolves a static resource the plugin ships: below {@code static} next to its descriptor, or below
     * {@code META-INF/static} or {@code static} where its classes were loaded from.
     *
     * @param path the relative path to the static resource
     * @return the resource or {@code null} if it doesn't exist
     */
    public @Nullable Resource getResource(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        Resource descriptorResource = pluginInfo.getDescriptorResource();
        if (descriptorResource != null) {
            Resource resource = existingRelative(descriptorResource, "static" + normalizedPath);
            if (resource != null) {
                return resource;
            }
        }
        if (baseResource != null) {
            Resource metaInfStatic = existingRelative(baseResource, "META-INF/static" + normalizedPath);
            return metaInfStatic != null ? metaInfStatic : existingRelative(baseResource, "static" + normalizedPath);
        }
        return null;
    }

    private static @Nullable Resource existingRelative(Resource base, String relativePath) {
        try {
            Resource resource = base.createRelative(relativePath);
            return resource.exists() ? resource : null;
        }
        catch (IOException e) {
            return null;
        }
    }

    /**
     * Reads the plugin's message bundles for a locale: every {@code *.properties} at the root of the resources its
     * classes were loaded with whose name has no locale suffix, or the locale's language, country or both.
     *
     * <p>Note this method does not cache so clients should in general cache the results of this method.</p>
     *
     * @param locale the locale
     * @return the properties or {@code null} if none exist
     */
    public @Nullable Properties getProperties(final Locale locale) {
        if (baseResourcesResource == null) {
            return null;
        }
        StaticResourceLoader resourceLoader = new StaticResourceLoader();
        resourceLoader.setBaseResource(baseResourcesResource);
        ResourcePatternResolver patternResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        try {
            Resource[] resources = patternResolver.getResources('*' + PROPERTIES_EXTENSION);
            resources = resources.length > 0 ? filterResources(resources, locale) : resources;
            if (resources.length == 0) {
                return null;
            }
            Arrays.sort(resources, (o1, o2) -> Integer.compare(
                    StringUtils.countOccurrencesOf(o1.getFilename(), "_"),
                    StringUtils.countOccurrencesOf(o2.getFilename(), "_")));
            Properties properties = new Properties();
            loadFromResources(properties, resources);
            return properties;
        }
        catch (IOException e) {
            return null;
        }
    }

    private static Resource[] filterResources(Resource[] resources, Locale locale) {
        List<Resource> finalResources = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            String fn = resource.getFilename();
            if (fn.indexOf(UNDERSCORE) > -1) {
                if (fn.endsWith(UNDERSCORE + locale.toString() + PROPERTIES_EXTENSION) ||
                        fn.endsWith(UNDERSCORE + locale.getLanguage() + UNDERSCORE + locale.getCountry() + PROPERTIES_EXTENSION) ||
                        fn.endsWith(UNDERSCORE + locale.getLanguage() + PROPERTIES_EXTENSION)) {
                    finalResources.add(resource);
                }
            }
            else {
                finalResources.add(resource);
            }
        }
        return finalResources.toArray(new Resource[0]);
    }

    private static void loadFromResources(Properties properties, Resource[] resources) throws IOException {
        for (Resource messageResource : resources) {
            try (InputStream inputStream = messageResource.getInputStream()) {
                properties.load(new InputStreamReader(
                        inputStream, Charset.forName(System.getProperty("file.encoding", DEFAULT_PROPERTIES_ENCODING))));
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || o instanceof DefaultGrailsPlugin that &&
                getPluginClass().equals(that.getPluginClass()) && Objects.equals(getVersion(), that.getVersion());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getVersion(), getPluginClass());
    }

    @Override
    public String toString() {
        return "[" + getName() + ":" + getVersion() + "]";
    }
}
