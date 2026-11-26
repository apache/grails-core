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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.TypeFilter;

import grails.core.ArtefactHandler;
import grails.core.GrailsApplication;
import grails.core.support.ParentApplicationContextAware;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.Plugin;
import grails.plugins.exceptions.PluginException;
import grails.plugins.metadata.PluginDefinition;
import grails.spring.BeanBuilder;
import grails.util.CollectionUtils;
import grails.util.Environment;
import grails.util.GrailsArrayUtils;
import org.apache.grails.core.plugins.PluginDescriptor;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.core.io.CachingPathMatchingResourcePatternResolver;
import org.grails.core.io.SpringResource;
import org.grails.plugins.support.WatchPattern;
import org.grails.plugins.support.WatchPatternParser;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * A Grails plugin: an instance of the plugin's {@link Plugin} subclass, described by its
 * {@link PluginDefinition}.
 *
 * <p>Everything known before the plugin runs, its name, version, ordering, environments and watched
 * resources, comes from the definition; the plugin's behaviour is its overridden {@code Plugin} methods.</p>
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@SuppressWarnings("rawtypes")
public class DefaultGrailsPlugin extends AbstractGrailsPlugin implements ParentApplicationContextAware {

    public static final String INCLUDES = "includes";
    public static final String EXCLUDES = "excludes";

    protected static final Log LOG = LogFactory.getLog(DefaultGrailsPlugin.class);

    private static final String PLUGIN_CHANGE_EVENT_CTX = "ctx";
    private static final String PLUGIN_CHANGE_EVENT_APPLICATION = "application";
    private static final String PLUGIN_CHANGE_EVENT_PLUGIN = "plugin";
    private static final String PLUGIN_CHANGE_EVENT_SOURCE = "source";
    private static final String PLUGIN_CHANGE_EVENT_MANAGER = "manager";

    private Plugin plugin;
    private PluginDefinition definition;
    private Resource[] watchedResources = {};
    private final PathMatchingResourcePatternResolver resolver = CachingPathMatchingResourcePatternResolver.INSTANCE;
    private String[] watchedResourcePatternReferences;
    private List<WatchPattern> watchedResourcePatterns;
    private Class<?>[] providedArtefacts = {};
    private Map<String, Set<Object>> pluginEnvs = new HashMap<>();
    private @Nullable Resource pluginDescriptor;

    /**
     * A plugin class registered directly, described by its {@code @GrailsPluginDescriptor}.
     */
    public DefaultGrailsPlugin(Class<?> pluginClass, GrailsApplication application) {
        this(pluginClass, (Resource) null, application);
    }

    /**
     * A plugin class registered directly, described by its {@code @GrailsPluginDescriptor}.
     *
     * @param resource the source file the class was compiled from, which {@link #refresh()} recompiles
     */
    public DefaultGrailsPlugin(Class<?> pluginClass, @Nullable Resource resource, GrailsApplication application) {
        this(pluginClass, new PluginDescriptor(resource, PluginUtils.definitionOf(pluginClass)), application);
    }

    /**
     * A plugin described by its descriptor.
     */
    public DefaultGrailsPlugin(Class<?> pluginClass, PluginDescriptor descriptor, GrailsApplication application) {
        super(pluginClass, descriptor.getDefinition(), application);
        this.pluginDescriptor = descriptor.getResource();
        try {
            initialisePlugin(pluginClass, descriptor.getDefinition());
        }
        catch (PluginException e) {
            throw e;
        }
        catch (RuntimeException e) {
            throw new PluginException("Error initialising plugin for class [" + pluginClass.getName() + "]: " + e.getMessage(), e);
        }
    }

    private void initialisePlugin(Class<?> clazz, PluginDefinition pluginDefinition) {
        PluginUtils.extractPluginMetadata(new PluginDescriptor(pluginDescriptor, pluginDefinition), clazz);
        this.pluginClass = clazz;
        this.definition = pluginDefinition;
        this.plugin = instantiate(clazz);
        plugin.setApplicationContext(applicationContext);
        plugin.setPlugin(this);
        plugin.setGrailsApplication(grailsApplication);
        plugin.setPluginManager(manager);
        plugin.enabled = definition.isEnabled();

        dependencies = new HashMap<>(definition.getDependsOn());
        dependencyNames = definition.getDependsOn().keySet().toArray(String[]::new);
        evictionList = definition.getEvict().toArray(String[]::new);
        pluginEnvs = new HashMap<>(environments(definition));
        providedArtefacts = loadClasses(definition.getProvidedArtefacts());
        evaluateWatchedResources();
    }

    private static Plugin instantiate(Class<?> clazz) {
        try {
            return (Plugin) clazz.getDeclaredConstructor().newInstance();
        }
        catch (InvocationTargetException e) {
            throw new PluginException("Plugin [" + clazz.getName() + "] could not be created: " + e.getCause().getMessage(), e.getCause());
        }
        catch (ReflectiveOperationException e) {
            throw new PluginException("Plugin [" + clazz.getName() + "] must have a public no-argument constructor", e);
        }
    }

    private static Map<String, Set<Object>> environments(PluginDefinition definition) {
        Map<String, Set<Object>> environments = new HashMap<>();
        if (!definition.getEnvironmentsIncludes().isEmpty()) {
            environments.put(INCLUDES, environmentNames(definition.getEnvironmentsIncludes()));
        }
        if (!definition.getEnvironmentsExcludes().isEmpty()) {
            environments.put(EXCLUDES, environmentNames(definition.getEnvironmentsExcludes()));
        }
        return environments;
    }

    private static Set<Object> environmentNames(List<String> names) {
        Set<Object> values = new HashSet<>();
        for (String name : names) {
            Environment environment = Environment.getEnvironment(name);
            values.add(environment != null ? environment.getName() : name);
        }
        return values;
    }

    /** Loads the classes a definition names, with the application's class loader. */
    protected Class<?>[] loadClasses(List<String> classNames) {
        ClassLoader classLoader = grailsApplication != null ? grailsApplication.getClassLoader() : pluginClass.getClassLoader();
        List<Class<?>> classes = new ArrayList<>();
        for (String className : classNames) {
            try {
                classes.add(classLoader.loadClass(className));
            }
            catch (ClassNotFoundException | LinkageError e) {
                throw new PluginException("Failed to load class [" + className + "] of plugin [" + getName() + "]: " + e.getMessage(), e);
            }
        }
        return classes.toArray(new Class<?>[0]);
    }

    /** @return the plugin's definition */
    public PluginDefinition getDefinition() {
        return definition;
    }

    @Override
    public boolean isEnabled(String[] activeProfiles) {
        return true;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        super.setApplicationContext(applicationContext);
        plugin.setApplicationContext(applicationContext);
        if (plugin instanceof ApplicationListener listener && applicationContext instanceof ConfigurableApplicationContext context) {
            context.addApplicationListener(listener);
        }
    }

    @Override
    public List<WatchPattern> getWatchedResourcePatterns() {
        return watchedResourcePatterns;
    }

    @Override
    public boolean hasInterestInChange(String path) {
        if (watchedResourcePatterns != null) {
            for (WatchPattern watchedResourcePattern : watchedResourcePatterns) {
                if (watchedResourcePattern.matchesPath(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void setManager(GrailsPluginManager manager) {
        super.setManager(manager);
        plugin.setPluginManager(manager);
    }

    private void evaluateWatchedResources() {
        Environment env = Environment.getCurrent();
        if (env.isWarDeployed() && !env.isReloadEnabled()) {
            return;
        }
        List<String> patterns = definition.getWatchedResources();
        if (patterns.isEmpty()) {
            return;
        }
        String baseLocation = env.getReloadLocation();
        List<String> references = new ArrayList<>();
        for (String pattern : patterns) {
            references.add(baseLocation == null ? pattern : getResourcePatternForBaseLocation(baseLocation, pattern));
        }
        watchedResourcePatternReferences = references.toArray(new String[0]);
        try {
            watchedResourcePatterns = new WatchPatternParser().getWatchPatterns(references);
        }
        catch (IllegalArgumentException e) {
            throw new PluginException("Plugin [" + getName() + "] declares invalid watched resources " + references + ": " + e.getMessage(), e);
        }
    }

    private String getResourcePatternForBaseLocation(String baseLocation, String resourcePath) {
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

    @Override
    public String[] getLoadAfterNames() {
        return definition.getLoadAfter().toArray(String[]::new);
    }

    @Override
    public String[] getLoadBeforeNames() {
        return definition.getLoadBefore().toArray(String[]::new);
    }

    /**
     * @return the resolver
     */
    public PathMatchingResourcePatternResolver getResolver() {
        return resolver;
    }

    public ApplicationContext getParentCtx() {
        return grailsApplication.getParentContext();
    }

    public BeanBuilder beans(Closure closure) {
        BeanBuilder bb = new BeanBuilder(getParentCtx(), new GroovyClassLoader(grailsApplication.getClassLoader()));
        bb.invokeMethod("beans", new Object[]{closure});
        return bb;
    }

    public void doWithApplicationContext(ApplicationContext ctx) {
        plugin.setApplicationContext(ctx);
        plugin.doWithApplicationContext();
    }

    public void doWithRuntimeConfiguration(RuntimeSpringConfiguration springConfig) {
        Binding b = new Binding();
        b.setVariable("application", grailsApplication);
        b.setVariable(GrailsApplication.APPLICATION_ID, grailsApplication);
        b.setVariable("manager", getManager());
        b.setVariable("plugin", this);
        b.setVariable("parentCtx", getParentCtx());
        b.setVariable("resolver", getResolver());

        Closure c = plugin.doWithSpring();
        // A plugin defines at most one Spring configuration hook. Both forms are explicit overrides, so
        // defining both is an authoring error rather than a supported combination.
        if (c != null && isDoWithSpringMethodOverridden(plugin)) {
            throw new PluginException("Plugin [" + this + "] defines both the closure-returning doWithSpring() " +
                    "and the doWithSpring(BeanBuilder) method. Define only one Spring configuration hook.");
        }
        BeanBuilder bb = new BeanBuilder(getParentCtx(), springConfig, grailsApplication.getClassLoader());
        bb.setBinding(b);
        if (c != null) {
            c.setDelegate(bb);
            bb.invokeMethod("beans", new Object[]{c});
        }
        plugin.doWithSpring(bb);
    }

    private static boolean isDoWithSpringMethodOverridden(Plugin pluginObject) {
        try {
            Method method = pluginObject.getClass().getMethod("doWithSpring", BeanBuilder.class);
            return method.getDeclaringClass() != Plugin.class;
        }
        catch (NoSuchMethodException e) {
            return false;
        }
    }

    @Override
    public BeanRegistrar getBeanRegistrar() {
        return plugin.beanRegistrar();
    }

    @SuppressWarnings("unchecked")
    private void addExcludeRuleInternal(Map map, Object o) {
        Collection excludes = (Collection) map.get(EXCLUDES);
        if (excludes == null) {
            excludes = new HashSet();
            map.put(EXCLUDES, excludes);
        }
        Collection includes = (Collection) map.get(INCLUDES);
        if (includes != null) {
            includes.remove(o);
        }
        excludes.add(o);
    }

    public void addExclude(Environment env) {
        addExcludeRuleInternal(pluginEnvs, env.getName());
    }

    public boolean supportsEnvironment(Environment environment) {
        return PluginUtils.supportsValueInIncludeExcludeMap(pluginEnvs, environment.getName());
    }

    public boolean supportsCurrentScopeAndEnvironment() {
        return supportsEnvironment(Environment.getCurrent());
    }

    @Override
    public String[] getDependencyNames() {
        return dependencyNames;
    }

    /**
     * @return the watchedResources
     */
    public Resource[] getWatchedResources() {
        if (watchedResources.length == 0 && watchedResourcePatternReferences != null) {
            for (String resourcesReference : watchedResourcePatternReferences) {
                try {
                    Resource[] resources = resolver.getResources(resourcesReference);
                    if (resources.length > 0) {
                        watchedResources = (Resource[]) GrailsArrayUtils.addAll(watchedResources, resources);
                    }
                }
                catch (IOException e) {
                    LOG.debug("Cannot resolve watched resources [" + resourcesReference + "] of plugin " + this + ": " + e.getMessage());
                }
            }
        }
        return watchedResources;
    }

    @Override
    public String getDependentVersion(String name) {
        Object dependentVersion = dependencies.get(name);
        if (dependentVersion == null) {
            throw new PluginException("Plugin [" + getName() + "] referenced dependency [" + name + "] with no version!");
        }
        return dependentVersion.toString();
    }

    @Override
    public String toString() {
        return "[" + getName() + ":" + getVersion() + "]";
    }

    public void setWatchedResources(Resource[] watchedResources) throws IOException {
        this.watchedResources = watchedResources;
    }

    /*
     * These two properties help the closures to resolve a log and plugin variable during executing
     */
    public Log getLog() {
        return LOG;
    }

    public GrailsPlugin getPlugin() {
        return this;
    }

    public void setParentApplicationContext(ApplicationContext parent) {
        // do nothing for the moment
    }

    /**
     * Recompiles the plugin class from the source file it was registered with, when it changes in
     * development.
     */
    @Override
    public void refresh() {
        org.grails.io.support.Resource descriptor = getDescriptor();
        if (grailsApplication == null || descriptor == null) {
            return;
        }
        GroovyClassLoader gcl = new GroovyClassLoader(grailsApplication.getClassLoader());
        try {
            Class<?> recompiled = gcl.parseClass(descriptor.getFile());
            initialisePlugin(recompiled, PluginUtils.definitionOf(recompiled));
        }
        catch (IOException | RuntimeException e) {
            LOG.error("Error refreshing plugin: " + e.getMessage(), e);
        }
    }

    @Override
    public GroovyObject getInstance() {
        return plugin;
    }

    @Override
    public Object getPluginInstance() {
        return plugin;
    }

    public void doWithDynamicMethods(ApplicationContext ctx) {
        plugin.doWithDynamicMethods();
    }

    public boolean isEnabled() {
        return plugin.enabled;
    }

    public String[] getObservedPluginNames() {
        return definition.getObserve().toArray(String[]::new);
    }

    public void notifyOfEvent(Map event) {
        plugin.onChange(event);
    }

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

    public void doArtefactConfiguration() {
        for (ArtefactHandler handler : plugin.getArtefacts()) {
            grailsApplication.registerArtefactHandler(handler);
        }
    }

    public Class<?>[] getProvidedArtefacts() {
        return providedArtefacts;
    }

    public List<String> getPluginExcludes() {
        return definition.getPluginExcludes();
    }

    public Collection<? extends TypeFilter> getTypeFilters() {
        return plugin.getTypeFilters();
    }

    public String getFullName() {
        return getName() + '-' + getVersion();
    }

    public org.grails.io.support.@Nullable Resource getDescriptor() {
        return pluginDescriptor != null ? new SpringResource(pluginDescriptor) : null;
    }

    public void setDescriptor(@Nullable Resource descriptor) {
        pluginDescriptor = descriptor;
    }

    public org.grails.io.support.@Nullable Resource getPluginDir() {
        if (pluginDescriptor == null) {
            return null;
        }
        try {
            return new SpringResource(pluginDescriptor.createRelative("."));
        }
        catch (IOException e) {
            return null;
        }
    }

    public Map getProperties() {
        return plugin != null ? DefaultGroovyMethods.getProperties(plugin) : Collections.emptyMap();
    }
}
