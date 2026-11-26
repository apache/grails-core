/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.grails.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClassRegistry;
import groovy.util.ConfigObject;
import groovy.util.ConfigSlurper;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterRegistry;
import org.springframework.core.convert.support.GenericConversionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import grails.artefact.Enhanced;
import grails.core.ArtefactHandler;
import grails.core.GrailsApplication;
import grails.plugins.CompatibilityPluginFilter;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.plugins.Plugin;
import grails.plugins.PluginFilter;
import grails.plugins.exceptions.PluginException;
import grails.util.Environment;
import grails.util.GrailsNameUtils;
import org.apache.grails.core.plugins.PluginDiscovery;
import org.apache.grails.core.plugins.PluginInfo;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.config.NavigableMap;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.plugins.support.WatchPattern;
import org.grails.spring.RuntimeSpringConfiguration;

/**
 * Abstract implementation of the GrailsPluginManager interface
 *
 * @author Graeme Rocher
 * @since 0.4
 */
public abstract class AbstractGrailsPluginManager implements GrailsPluginManager {

    private static final Log LOG = LogFactory.getLog(AbstractGrailsPluginManager.class);
    private static final String BLANK = "";
    public static final String CONFIG_FILE = "application.groovy";
    protected GrailsApplication application;
    protected PluginDiscovery pluginDiscovery;
    protected Map<String, GrailsPlugin> plugins = new HashMap<>();
    protected boolean initialised = false;
    protected boolean shutdown = false;
    protected ApplicationContext applicationContext;

    private static final String CONFIG_BINDING_USER_HOME = "userHome";
    private static final String CONFIG_BINDING_APP_NAME = "appName";
    private static final String CONFIG_BINDING_APP_VERSION = "appVersion";

    public AbstractGrailsPluginManager(GrailsApplication application, PluginDiscovery pluginDiscovery) {
        Objects.requireNonNull(application, "Argument [application] cannot be null!");
        Objects.requireNonNull(pluginDiscovery, "Argument [pluginDiscovery] cannot be null!");
        this.application = application;
        this.pluginDiscovery = pluginDiscovery;
    }

    public List<TypeFilter> getTypeFilters() {
        return pluginDiscovery.getPluginsInTopologicalOrder()
                .stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .map(GrailsPlugin::getTypeFilters)
                .flatMap(Collection::stream)
                .map(tf -> (TypeFilter) tf)
                .toList();
    }

    public GrailsPlugin[] getAllPlugins() {
        return pluginDiscovery.getPluginsInTopologicalOrder()
                .stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toArray(GrailsPlugin[]::new);
    }

    public GrailsPlugin[] getFailedLoadPlugins() {
        return pluginDiscovery.getFailedPlugins()
                .stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .toArray(GrailsPlugin[]::new);
    }

    /**
     * @return the initialised
     */
    public boolean isInitialised() {
        return initialised;
    }

    protected void checkInitialised() {
        Assert.state(initialised, "Must call loadPlugins() before invoking configurational methods on GrailsPluginManager");
    }

    public GrailsPlugin getFailedPlugin(String name) {
        PluginInfo pluginInfo = pluginDiscovery.getFailedPlugin(name);
        return pluginInfo == null ? null : plugins.get(pluginInfo.getName());
    }

    /**
     * Base implementation that simply goes through the list of plugins and calls doWithRuntimeConfiguration on each
     * @param springConfig The RuntimeSpringConfiguration instance
     */
    public void doRuntimeConfiguration(RuntimeSpringConfiguration springConfig) {
        ApplicationContext context = springConfig.getUnrefreshedApplicationContext();
        AutowireCapableBeanFactory autowireCapableBeanFactory = context.getAutowireCapableBeanFactory();
        if (autowireCapableBeanFactory instanceof ConfigurableListableBeanFactory) {
            ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) autowireCapableBeanFactory;
            ConversionService existingConversionService = beanFactory.getConversionService();
            ConverterRegistry converterRegistry;
            if (existingConversionService == null) {
                GenericConversionService conversionService = new GenericConversionService();
                converterRegistry = conversionService;
                beanFactory.setConversionService(conversionService);
            } else {
                converterRegistry = (ConverterRegistry) existingConversionService;
            }

            // This can't be converted to a lambda because of how spring accesses it
            converterRegistry.addConverter(new Converter<NavigableMap.NullSafeNavigator, Object>() {
                @Override
                public Object convert(NavigableMap.NullSafeNavigator source) {
                    return null;
                }
            });
        }
        checkInitialised();
        for (PluginInfo pluginInfo : pluginDiscovery.getPluginsInTopologicalOrder()) {
            GrailsPlugin grailsPlugin = plugins.get(pluginInfo.getName());
            if (grailsPlugin.supportsCurrentScopeAndEnvironment() && grailsPlugin.isEnabled(context.getEnvironment().getActiveProfiles())) {
                grailsPlugin.doWithRuntimeConfiguration(springConfig);
            }
        }
    }

    /**
     * Base implementation that will perform runtime configuration for the specified plugin name.
     */
    public void doRuntimeConfiguration(String pluginName, RuntimeSpringConfiguration springConfig) {
        checkInitialised();
        GrailsPlugin plugin = getGrailsPlugin(pluginName);
        if (plugin == null) {
            throw new PluginException("Plugin [" + pluginName + "] not found");
        }

        if (!plugin.supportsCurrentScopeAndEnvironment()) {
            return;
        }

        if (!plugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles())) return;

        String[] dependencyNames = plugin.getDependencyNames();
        doRuntimeConfigurationForDependencies(dependencyNames, springConfig);
        String[] loadAfters = plugin.getLoadAfterNames();
        for (String name : loadAfters) {
            GrailsPlugin current = getGrailsPlugin(name);
            if (current != null) {
                current.doWithRuntimeConfiguration(springConfig);
            }
        }
        plugin.doWithRuntimeConfiguration(springConfig);
    }

    private void doRuntimeConfigurationForDependencies(String[] dependencyNames, RuntimeSpringConfiguration springConfig) {
        for (String dn : dependencyNames) {
            GrailsPlugin current = getGrailsPlugin(dn);
            if (current == null) {
                throw new PluginException("Cannot load Plugin. Dependency [" + current + "] not found");
            }

            String[] pluginDependencies = current.getDependencyNames();
            if (pluginDependencies.length > 0) {
                doRuntimeConfigurationForDependencies(pluginDependencies, springConfig);
            }
            if (isPluginDisabledForProfile(current)) continue;
            current.doWithRuntimeConfiguration(springConfig);
        }
    }

    /**
     * Base implementation that will simply go through each plugin and call doWithApplicationContext on each.
     */
    public void doPostProcessing(ApplicationContext ctx) {
        checkInitialised();

        for (PluginInfo pluginInfo : pluginDiscovery.getPluginsInTopologicalOrder()) {
            GrailsPlugin grailsPlugin = plugins.get(pluginInfo.getName());
            if (isPluginDisabledForProfile(grailsPlugin)) continue;
            if (grailsPlugin.supportsCurrentScopeAndEnvironment()) {
                grailsPlugin.doWithApplicationContext(ctx);
            }
        }
    }

    public GrailsPlugin getGrailsPlugin(String name) {
        PluginInfo pluginInfo = pluginDiscovery.findPlugin(name);
        if (pluginInfo == null) {
            return null;
        }

        return plugins.get(pluginInfo.getName());
    }

    public GrailsPlugin getGrailsPluginForClassName(String name) {
        return getGrailsPlugin(PluginUtils.getLogicalPluginNameFromClassName(name));
    }

    public GrailsPlugin getGrailsPlugin(String name, Object version) {
        PluginInfo pluginInfo = pluginDiscovery.findPlugin(name, version);
        if (pluginInfo == null) {
            return null;
        }

        return plugins.get(pluginInfo.getName());
    }

    public boolean hasGrailsPlugin(String name) {
        return pluginDiscovery.hasPlugin(name);
    }

    public void doDynamicMethods() {
        checkInitialised();
        Class<?>[] allClasses = application.getAllClasses();
        if (allClasses != null) {
            for (Class<?> c : allClasses) {
                ExpandoMetaClass emc = new ExpandoMetaClass(c, true, true);
                emc.initialize();
            }
            ApplicationContext ctx = applicationContext;
            for (GrailsPlugin plugin : getOrderedPlugins()) {
                if (!plugin.isEnabled(ctx.getEnvironment().getActiveProfiles())) continue;
                plugin.doWithDynamicMethods(ctx);
            }
        }
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
        if (applicationContext != null) {
            for (GrailsPlugin plugin : getOrderedPlugins()) {
                plugin.setApplicationContext(applicationContext);
            }
        }
    }

    public void setApplication(GrailsApplication application) {
        Assert.notNull(application, "Argument [application] cannot be null");
        this.application = application;

        for (GrailsPlugin plugin : getOrderedPlugins()) {
            plugin.setApplication(application);
        }
    }

    protected List<GrailsPlugin> getOrderedPlugins() {
        List<PluginInfo> orderedPluginInfos = pluginDiscovery.getPluginsInTopologicalOrder();
        if (orderedPluginInfos == null) {
            return new ArrayList<>();
        }

        return orderedPluginInfos.stream()
                .map(PluginInfo::getName)
                .map(plugins::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void registerProvidedArtefacts(GrailsApplication app) {
        checkInitialised();

        // since plugin classes are added as overridable artefacts, which are added as the first
        // item in the list of artefacts, we have to iterate in reverse order to ensure plugin
        // load sequence is maintained
        ArrayList<GrailsPlugin> toProcess = new ArrayList<>(getOrderedPlugins());
        Collections.reverse(toProcess);
        for (GrailsPlugin plugin : toProcess) {
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                if (isPluginDisabledForProfile(plugin)) continue;
                for (Class<?> artefact : plugin.getProvidedArtefacts()) {
                    String shortName = GrailsNameUtils.getShortName(artefact);
                    if (artefact.getName().equals(shortName)) {
                        LOG.warn("Plugin " + plugin.getName() + " has an artefact " + shortName + " without a package name " +
                                "This could lead to artefacts being excluded from the application");
                        if (app.getClassForName(shortName) != null) {
                            LOG.error("Plugin " + plugin.getName() + " has an artefact " + shortName + " that is being excluded from " +
                                    "the application because another artefact exists with the same name without a package defined.");
                        }
                    }
                    if (!isAlreadyRegistered(app, artefact)) {
                        app.addOverridableArtefact(artefact);
                    }
                }
            }
        }
    }

    private boolean isAlreadyRegistered(GrailsApplication app, Class<?> artefact) {
        return app.getClassForName(artefact.getName()) != null;
    }

    public void doArtefactConfiguration() {
        checkInitialised();
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            if (isPluginDisabledForProfile(plugin)) continue;
            if (plugin.supportsCurrentScopeAndEnvironment()) {
                plugin.doArtefactConfiguration();
            }
        }
    }

    protected boolean isPluginDisabledForProfile(GrailsPlugin plugin) {
        return applicationContext != null && !plugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles());
    }

    public void onStartup(Map<String, Object> event) {
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            if (plugin.getInstance() instanceof Plugin) {
                ((Plugin) plugin.getInstance()).onStartup(event);
            }
        }
    }

    public void shutdown() {
        checkInitialised();
        try {
            // Shutdown plugins in reverse dependency order
            List<GrailsPlugin> reversePluginList = new ArrayList<>(getOrderedPlugins());
            Collections.reverse(reversePluginList);

            for (GrailsPlugin plugin : reversePluginList) {
                if (!plugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles())) continue;
                if (plugin.supportsCurrentScopeAndEnvironment()) {
                    plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_SHUTDOWN, plugin);
                }
            }
        } finally {
            shutdown = true;
        }

    }

    public boolean isShutdown() {
        return shutdown;
    }

    /**
     * @deprecated Plugin filtering is now handled by {@link PluginDiscovery}.
     * Use {@link PluginDiscovery#setPluginFilter(PluginFilter)} instead.
     * This method will be removed in Grails 8.0.0.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    @Override
    public void setPluginFilter(PluginFilter pluginFilter) {
        pluginDiscovery.setPluginFilter(new CompatibilityPluginFilter(pluginFilter, plugins.values()));
        reinitializeDiscovery();
    }

    /**
     * @deprecated Core plugin loading is now handled by {@link PluginDiscovery}.
     * Use {@link PluginDiscovery#setLoadPluginsFromClasspath(boolean)} instead.
     * This method will be removed in Grails 8.0.0.
     */
    @Deprecated(forRemoval = true, since = "7.1")
    @Override
    public void setLoadCorePlugins(boolean shouldLoadCorePlugins) {
        pluginDiscovery.setLoadPluginsFromClasspath(shouldLoadCorePlugins);
        reinitializeDiscovery();
    }

    /**
     * Resets and reinitializes plugin discovery if an application context is available.
     * When called before the application context is set (the typical pre-startup case),
     * the forwarded settings will be picked up during normal lifecycle initialization.
     */
    private void reinitializeDiscovery() {
        if (applicationContext != null) {
            pluginDiscovery.reset();
            pluginDiscovery.init(applicationContext.getEnvironment());
        }
    }

    public void informOfClassChange(Class<?> aClass) {
        if (aClass == null || application == null) {
            return;
        }

        ArtefactHandler handler = application.getArtefactType(aClass);
        if (handler == null) {
            return;
        }

        String pluginName = handler.getPluginName();
        if (pluginName == null) {
            return;
        }

        GrailsPlugin plugin = getGrailsPlugin(pluginName);
        if (plugin != null) {
            if (!plugin.isEnabled(applicationContext.getEnvironment().getActiveProfiles())) return;
            plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, aClass);
        } else {
            String classNameAsPath = aClass.getName().replace('.', File.separatorChar);
            String groovyClass = classNameAsPath + ".groovy";
            String javaClass = classNameAsPath + ".java";
            for (GrailsPlugin grailsPlugin : getOrderedPlugins()) {
                List<WatchPattern> watchPatterns = grailsPlugin.getWatchedResourcePatterns();
                if (watchPatterns != null) {
                    for (WatchPattern watchPattern : watchPatterns) {
                        File parent = watchPattern.getDirectory();
                        String extension = watchPattern.getExtension();

                        if (parent != null && extension != null) {
                            File f = new File(parent, groovyClass);
                            if (f.exists() && f.getName().endsWith(extension)) {
                                grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, aClass);
                            } else {
                                f = new File(parent, javaClass);
                                if (f.exists() && f.getName().endsWith(extension)) {
                                    grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, aClass);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public String getPluginPath(String name) {
        return getPluginPath(name, false);
    }

    public String getPluginPath(String name, boolean forceCamelCase) {
        GrailsPlugin plugin = getGrailsPlugin(name);
        if (plugin != null && !plugin.isBasePlugin()) {
            if (forceCamelCase) {
                return plugin.getPluginPathCamelCase();
            } else {
                return plugin.getPluginPath();
            }
        }
        return BLANK;
    }

    public String getPluginPathForInstance(Object instance) {
        if (instance != null) {
            return getPluginPathForClass(instance.getClass());
        }
        return null;
    }

    public GrailsPlugin getPluginForInstance(Object instance) {
        if (instance != null) {
            return getPluginForClass(instance.getClass());
        }
        return null;
    }

    public GrailsPlugin getPluginForClass(Class<?> theClass) {
        if (theClass != null) {
            grails.plugins.metadata.GrailsPlugin ann =
                    theClass.getAnnotation(grails.plugins.metadata.GrailsPlugin.class);
            if (ann != null) {
                return getGrailsPlugin(ann.name());
            }
        }
        return null;
    }

    @Override
    public void informPluginsOfConfigChange() {
        for (GrailsPlugin plugin : getOrderedPlugins()) {
            plugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CONFIG_CHANGE, application.getConfig());
        }
    }

    public void informOfFileChange(File file) {
        String className = GrailsResourceUtils.getClassName(file.getAbsolutePath());
        Class<?> cls = null;
        if (className != null) {
            cls = loadApplicationClass(className);
        }
        informOfClassChange(file, cls);
    }

    static ConfigSlurper getConfigSlurper(GrailsApplication application) {
        String environment = Environment.getCurrent().getName();
        ConfigSlurper configSlurper = new ConfigSlurper(environment);
        final Map<String, Object> binding = new HashMap<>();
        // configure config slurper binding
        binding.put(CONFIG_BINDING_USER_HOME, System.getProperty("user.home"));
        if (application != null) {
            binding.put(CONFIG_BINDING_APP_NAME, application.getMetadata().getApplicationName());
            binding.put(CONFIG_BINDING_APP_VERSION, application.getMetadata().getApplicationVersion());
            binding.put(GrailsApplication.APPLICATION_ID, application);
        }
        configSlurper.setBinding(binding);
        return configSlurper;
    }

    public void informOfClassChange(File file, @SuppressWarnings("rawtypes") Class cls) {
        if (file.getName().equals(CONFIG_FILE)) {
            ConfigSlurper configSlurper = getConfigSlurper(application);
            ConfigObject c;
            try {
                c = configSlurper.parse(file.toURI().toURL());
                application.getConfig().merge(c);
                final Map flat = c.flatten();
                application.getConfig().merge(flat);
                application.configChanged();
                informPluginsOfConfigChange();
            } catch (Exception e) {
                // ignore
                LOG.debug("Error in changing Config", e);
            }
        } else {

            if (cls != null) {
                MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
                registry.removeMetaClass(cls);
                ExpandoMetaClass newMc = new ExpandoMetaClass(cls, true, true);
                newMc.initialize();
                registry.setMetaClass(cls, newMc);

                Enhanced en = AnnotationUtils.findAnnotation(cls, Enhanced.class);
                if (en != null) {
                    Class<?>[] mixinClasses = en.mixins();
                    if (mixinClasses != null) {
                        DefaultGroovyMethods.mixin(newMc, mixinClasses);
                    }
                }
            }

            for (GrailsPlugin grailsPlugin : getOrderedPlugins()) {
                if (grailsPlugin.hasInterestInChange(file.getAbsolutePath())) {
                    try {
                        if (cls == null) {
                            grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, new FileSystemResource(file));
                        } else {
                            grailsPlugin.notifyOfEvent(GrailsPlugin.EVENT_ON_CHANGE, cls);
                        }
                        Environment.setCurrentReloadError(null);
                    } catch (Exception e) {
                        LOG.error("Plugin " + grailsPlugin + " could not reload changes to file [" +
                                file + "]: " + e.getMessage(), e);
                        Environment.setCurrentReloadError(e);
                    }
                }
            }
        }
    }

    private Class<?> loadApplicationClass(String className) {
        Class<?> cls = null;
        try {
            cls = application.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            // ignore
        }
        return cls;
    }

    public String getPluginPathForClass(Class<?> theClass) {
        if (theClass != null) {
            grails.plugins.metadata.GrailsPlugin ann =
                    theClass.getAnnotation(grails.plugins.metadata.GrailsPlugin.class);
            if (ann != null) {
                return getPluginPath(ann.name());
            }
        }
        return null;
    }

    public String getPluginViewsPathForInstance(Object instance) {
        if (instance != null) {
            return getPluginViewsPathForClass(instance.getClass());
        }
        return null;
    }

    public String getPluginViewsPathForClass(Class<?> theClass) {
        if (theClass != null) {
            final String path = getPluginPathForClass(theClass);
            if (StringUtils.hasText(path)) {
                return path + '/' + GrailsResourceUtils.GRAILS_APP_DIR + "/views";
            }
        }
        return null;
    }
}
