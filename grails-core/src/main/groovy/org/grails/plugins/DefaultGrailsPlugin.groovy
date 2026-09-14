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
package org.grails.plugins

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import groovy.transform.CompileStatic
import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.BeansException
import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.type.filter.TypeFilter

import grails.core.ArtefactHandler
import grails.core.GrailsApplication
import grails.core.GrailsApplicationLifeCycle
import grails.core.support.GrailsApplicationAware
import grails.core.support.ParentApplicationContextAware
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.plugins.Plugin
import grails.plugins.exceptions.PluginException
import grails.spring.BeanBuilder
import grails.util.CollectionUtils
import grails.util.Environment
import grails.util.GrailsArrayUtils
import grails.util.GrailsClassUtils
import grails.util.GrailsUtil
import org.apache.grails.core.plugins.PluginUtils
import org.grails.core.io.CachingPathMatchingResourcePatternResolver
import org.grails.core.io.SpringResource
import org.grails.plugins.support.WatchPattern
import org.grails.plugins.support.WatchPatternParser
import org.grails.spring.RuntimeSpringConfiguration

/**
 * Implementation of the GrailsPlugin interface that wraps a Groovy plugin class
 * and provides the magic to invoke its various methods from Java.
 *
 * @author Graeme Rocher
 * @since 0.4
 */
@SuppressWarnings('rawtypes')
@CompileStatic
class DefaultGrailsPlugin extends AbstractGrailsPlugin implements ParentApplicationContextAware {

    public static final String INCLUDES = 'includes'
    public static final String EXCLUDES = 'excludes'

    protected static final Log LOG = LogFactory.getLog(DefaultGrailsPlugin)

    private static final String PLUGIN_CHANGE_EVENT_CTX = 'ctx'
    private static final String PLUGIN_CHANGE_EVENT_APPLICATION = 'application'
    private static final String PLUGIN_CHANGE_EVENT_PLUGIN = 'plugin'
    private static final String PLUGIN_CHANGE_EVENT_SOURCE = 'source'
    private static final String PLUGIN_CHANGE_EVENT_MANAGER = 'manager'

    private GrailsPluginClass pluginGrailsClass

    private GroovyObject plugin
    protected BeanWrapper pluginBean
    private Closure onChangeListener
    private Resource[] watchedResources = [] as Resource[]

    private PathMatchingResourcePatternResolver resolver
    private String[] watchedResourcePatternReferences
    private String[] loadAfterNames = [] as String[]
    private String[] loadBeforeNames = [] as String[]
    private String status = STATUS_ENABLED
    private String[] observedPlugins
    private Closure onConfigChangeListener
    private Closure onShutdownListener
    private Class<?>[] providedArtefacts = [] as Class[]
    private Collection profiles = null
    private Map pluginEnvs
    private List<String> pluginExcludes = new ArrayList<>()
    private Collection<? extends TypeFilter> typeFilters = new ArrayList<>()
    private Resource pluginDescriptor
    private List<WatchPattern> watchedResourcePatterns

    DefaultGrailsPlugin(Class<?> pluginClass, Resource resource, GrailsApplication application) {
        super(pluginClass, application)
        // create properties
        this.@dependencies = Collections.emptyMap()
        pluginDescriptor = resource
        resolver = CachingPathMatchingResourcePatternResolver.INSTANCE

        try {
            initialisePlugin(pluginClass)
        } catch (Throwable e) {
            throw new PluginException('Error initialising plugin for class [' + pluginClass.getName() + ']:' + e.getMessage(), e)
        }
    }

    @Override
    boolean isEnabled(String[] activeProfiles) {
        if (profiles == null) return true
        else {
            for (String activeProfile in activeProfiles) {
                if (profiles.contains(activeProfile)) return true
            }
        }
        return false
    }

    @Override
    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        super.setApplicationContext(applicationContext)
        if (this.plugin instanceof ApplicationContextAware) {
            ((ApplicationContextAware) plugin).setApplicationContext(applicationContext)
        }
        if (this.plugin instanceof ApplicationListener) {
            ((ConfigurableApplicationContext) applicationContext).addApplicationListener((ApplicationListener) plugin)
        }
    }

    @Override
    List<WatchPattern> getWatchedResourcePatterns() {
        return watchedResourcePatterns
    }

    @Override
    boolean hasInterestInChange(String path) {
        if (watchedResourcePatterns != null) {
            for (WatchPattern watchedResourcePattern in watchedResourcePatterns) {
                if (watchedResourcePattern.matchesPath(path)) {
                    return true
                }
            }
        }
        return false
    }

    @Override
    void setManager(GrailsPluginManager manager) {
        super.setManager(manager)
        if (plugin instanceof Plugin) {
            ((Plugin) plugin).setPluginManager(manager)
        }
    }

    private void initialisePlugin(Class<?> clazz) {
        pluginGrailsClass = new GrailsPluginClass(clazz)
        plugin = (GroovyObject) pluginGrailsClass.newInstance()
        if (plugin instanceof Plugin) {
            Plugin p = (Plugin) plugin
            p.setApplicationContext(applicationContext)
            p.setPlugin(this)
            p.setGrailsApplication(grailsApplication)
            p.setPluginManager(manager)
        }
        else if (plugin instanceof GrailsApplicationAware) {
            ((GrailsApplicationAware) plugin).setGrailsApplication(grailsApplication)
        }
        pluginBean = new BeanWrapperImpl(plugin)

        // configure plugin
        this.@version = PluginUtils.evaluatePluginVersion(pluginBean, plugin, pluginGrailsClass.getName())

        PluginUtils.PluginDependencies pluginDependencies = PluginUtils.evaluatePluginDependencies(pluginBean, plugin)
        this.@dependencies = pluginDependencies.dependencies()
        this.@dependencyNames = pluginDependencies.dependencyNames()
        loadAfterNames = PluginUtils.evaluatePluginLoadAfters(pluginBean, plugin)
        loadBeforeNames = PluginUtils.evaluatePluginLoadBefores(pluginBean, plugin)
        evaluateProvidedArtefacts()
        this.@evictionList = PluginUtils.evaluatePluginEvictionPolicy(pluginBean, plugin)
        evaluateOnChangeListener()
        observedPlugins = PluginUtils.evaluateObservedPlugins(pluginBean, plugin)
        status = PluginUtils.evaluatePluginStatus(pluginBean, plugin)
        pluginEnvs = PluginUtils.evaluatePluginEnvironments(pluginBean, plugin)
        evaluatePluginExcludes()
        evaluateTypeFilters()
    }

    @SuppressWarnings('unchecked')
    private void evaluateTypeFilters() {
        Object result = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, TYPE_FILTERS)
        if (result instanceof List) {
            typeFilters = (List<TypeFilter>) result
        }
    }

    @SuppressWarnings('unchecked')
    private void evaluatePluginExcludes() {
        Object result = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, PLUGIN_EXCLUDES)
        if (result instanceof List) {
            pluginExcludes = (List<String>) result
        }
    }

    @SuppressWarnings('unchecked')
    private void evaluateProvidedArtefacts() {
        Object result = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(pluginBean, plugin, PROVIDED_ARTEFACTS)
        if (result instanceof Collection) {
            final Collection artefactList = (Collection) result
            providedArtefacts = (Class<?>[]) artefactList.toArray(new Class[artefactList.size()])
        }
    }

    DefaultGrailsPlugin(Class<?> pluginClass, GrailsApplication application) {
        this(pluginClass, null, application)
    }

    private void evaluateOnChangeListener() {
        if (pluginBean.isReadableProperty(ON_SHUTDOWN)) {
            onShutdownListener = (Closure) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, ON_SHUTDOWN)
        }
        if (pluginBean.isReadableProperty(ON_CONFIG_CHANGE)) {
            onConfigChangeListener = (Closure) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, ON_CONFIG_CHANGE)
        }
        if (pluginBean.isReadableProperty(ON_CHANGE)) {
            onChangeListener = (Closure) GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, ON_CHANGE)
        }

        Environment env = Environment.getCurrent()
        final boolean warDeployed = env.isWarDeployed()
        final boolean reloadEnabled = env.isReloadEnabled()

        if (!((reloadEnabled || !warDeployed))) {
            return
        }

        Object referencedResources = GrailsClassUtils.getPropertyOrStaticPropertyOrFieldValue(plugin, WATCHED_RESOURCES)

        try {
            List resourceList = null
            if (referencedResources instanceof String) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug('Configuring plugin ' + this + ' to watch resources with pattern: ' + referencedResources)
                }
                resourceList = Collections.singletonList(referencedResources.toString())
            }
            else if (referencedResources instanceof List) {
                resourceList = (List) referencedResources
            }

            if (resourceList == null) {
                return
            }

            List<String> resourceListTmp = new ArrayList<>()
            final String baseLocation = env.getReloadLocation()

            for (Object ref in resourceList) {
                String stringRef = ref.toString()
                if (warDeployed) {
                    addBaseLocationPattern(resourceListTmp, baseLocation, stringRef)
                }
                else {
                    addBaseLocationPattern(resourceListTmp, baseLocation, stringRef)
                }
            }

            watchedResourcePatternReferences = new String[resourceListTmp.size()]
            for (int i = 0; i < watchedResourcePatternReferences.length; i++) {
                String resRef = resourceListTmp.get(i)
                watchedResourcePatternReferences[i] = resRef
            }

            watchedResourcePatterns = new WatchPatternParser().getWatchPatterns(Arrays.asList(watchedResourcePatternReferences))
        }
        catch (IllegalArgumentException e) {
            if (GrailsUtil.isDevelopmentEnv()) {
                @SuppressWarnings('RedundantCast')
                String message = 'Cannot load plug-in resource watch list from [' + GrailsArrayUtils.toString((Object[]) watchedResourcePatternReferences) +
                        ']. This means that the plugin ' + this +
                        ', will not be able to auto-reload changes effectively. Try running grails upgrade.: ' + e.getMessage()
                LOG.debug(message)
            }
        }

    }

    private void addBaseLocationPattern(List<String> resourceList, final String baseLocation, String pattern) {
        resourceList.add(baseLocation == null ? pattern : getResourcePatternForBaseLocation(baseLocation, pattern))
    }

    private String getResourcePatternForBaseLocation(String baseLocation, String resourcePath) {
        String location = baseLocation
        if (!location.endsWith(File.separator)) location = location + File.separator
        if (resourcePath.startsWith('./')) {
            return 'file:' + location + resourcePath.substring(2)
        }
        else if (resourcePath.startsWith('file:./')) {
            return 'file:' + location + resourcePath.substring(7)
        }
        return resourcePath
    }

    @Override
    String[] getLoadAfterNames() {
        return loadAfterNames
    }

    @Override
    String[] getLoadBeforeNames() {
        return loadBeforeNames
    }

    /**
     * @return the resolver
     */
    PathMatchingResourcePatternResolver getResolver() {
        return resolver
    }

    ApplicationContext getParentCtx() {
        return grailsApplication.getParentContext()
    }

    BeanBuilder beans(Closure closure) {
        BeanBuilder bb = new BeanBuilder(getParentCtx(), new GroovyClassLoader(grailsApplication.getClassLoader()))
        bb.invokeMethod('beans', [closure] as Object[])
        return bb
    }

    void doWithApplicationContext(ApplicationContext ctx) {
        if (plugin instanceof Plugin) {
            Plugin pluginObject = (Plugin) plugin

            pluginObject.setApplicationContext(ctx)
            pluginObject.doWithApplicationContext()
        }
        else {
            Object[] args = [ctx] as Object[]
            invokePluginHook(DO_WITH_APPLICATION_CONTEXT, args, ctx)
        }
    }

    private void invokePluginHook(String methodName, Object[] args, ApplicationContext ctx) {
        if (pluginBean.isReadableProperty(methodName)) {
            Closure c = (Closure) plugin.getProperty(methodName)
            c.setDelegate(this)
            c.call(args)
        }
        else {
            MetaClass pluginMetaClass = pluginGrailsClass.getMetaClass()
            if (!pluginMetaClass.respondsTo(plugin, methodName, args).isEmpty()) {
                pluginMetaClass.invokeMethod(plugin, methodName, ctx)
            }
        }
    }

    void doWithRuntimeConfiguration(RuntimeSpringConfiguration springConfig) {
        Binding b = new Binding()
        b.setVariable('application', grailsApplication)
        b.setVariable(GrailsApplication.APPLICATION_ID, grailsApplication)
        b.setVariable('manager', getManager())
        b.setVariable('plugin', this)
        b.setVariable('parentCtx', getParentCtx())
        b.setVariable('resolver', getResolver())

        if (plugin instanceof Plugin) {
            Plugin pluginObject = (Plugin) plugin
            // Legacy closure-returning hook: doWithSpring() returns a bean-defining closure
            Closure c = pluginObject.doWithSpring()
            // A plugin must define at most one Spring configuration hook. Both forms are explicit
            // overrides, so defining both is an authoring error rather than a supported combination.
            if (c != null && isDoWithSpringMethodOverridden(pluginObject)) {
                throw new PluginException('Plugin [' + this + '] defines both the closure-returning doWithSpring() ' +
                        'and the doWithSpring(BeanBuilder) method. Define only one Spring configuration hook.')
            }
            BeanBuilder bb = new BeanBuilder(getParentCtx(), springConfig, grailsApplication.getClassLoader())
            bb.setBinding(b)
            if (c != null) {
                c.setDelegate(bb)
                bb.invokeMethod('beans', [c] as Object[])
            }
            // Method-based hook: doWithSpring(BeanBuilder) registers beans directly against the builder
            pluginObject.doWithSpring(bb)
        } else {
            if (!pluginBean.isReadableProperty(DO_WITH_SPRING)) {
                return
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug('Plugin ' + this + ' is participating in Spring configuration...')
            }

            Closure c = (Closure) plugin.getProperty(DO_WITH_SPRING)
            BeanBuilder bb = new BeanBuilder(getParentCtx(), springConfig, grailsApplication.getClassLoader())
            bb.setBinding(b)
            c.setDelegate(bb)
            bb.invokeMethod('beans', [c] as Object[])
        }

    }

    /**
     * Determines whether the plugin overrides the {@link Plugin#doWithSpring(BeanBuilder)} method, as opposed to
     * inheriting the no-op base implementation. Used to detect a plugin that defines both the closure-returning and
     * method-based Spring configuration hooks.
     *
     * @param pluginObject the plugin instance
     * @return true if {@code doWithSpring(BeanBuilder)} is declared below {@link Plugin}
     */
    private boolean isDoWithSpringMethodOverridden(Plugin pluginObject) {
        try {
            Method method = pluginObject.getClass().getMethod(DO_WITH_SPRING, BeanBuilder)
            return method.getDeclaringClass() != Plugin
        }
        catch (NoSuchMethodException e) {
            return false
        }
    }

    @Override
    BeanRegistrar getBeanRegistrar() {
        if (plugin instanceof GrailsApplicationLifeCycle) {
            return ((GrailsApplicationLifeCycle) plugin).beanRegistrar()
        }
        return null
    }

    @Override
    String getName() {
        return pluginGrailsClass.getLogicalPropertyName()
    }

    @SuppressWarnings('unchecked')
    private void addExcludeRuleInternal(Map map, Object o) {
        Collection excludes = (Collection) map.get(EXCLUDES)
        if (excludes == null) {
            excludes = new ArrayList()
            map.put(EXCLUDES, excludes)
        }
        Collection includes = (Collection) map.get(INCLUDES)
        if (includes != null) includes.remove(o)
        excludes.add(o)
    }

    void addExclude(Environment env) {
        addExcludeRuleInternal(pluginEnvs, env)
    }

    boolean supportsEnvironment(Environment environment) {
        return supportsValueInIncludeExcludeMap(pluginEnvs, environment.getName())
    }

    boolean supportsCurrentScopeAndEnvironment() {
        Environment e = Environment.getCurrent()
        return supportsEnvironment(e)
    }

    private boolean supportsValueInIncludeExcludeMap(Map includeExcludeMap, Object value) {
        if (includeExcludeMap.isEmpty()) {
            return true
        }

        Set includes = (Set) includeExcludeMap.get(INCLUDES)
        if (includes != null) {
            return includes.contains(value)
        }

        Set excludes = (Set) includeExcludeMap.get(EXCLUDES)
        return !(excludes != null && excludes.contains(value))
    }

    @Override
    String[] getDependencyNames() {
        return dependencyNames
    }

    /**
     * @return the watchedResources
     */
    Resource[] getWatchedResources() {
        if (watchedResources.length == 0 && watchedResourcePatternReferences != null) {
            for (String resourcesReference in watchedResourcePatternReferences) {
                try {
                    Resource[] resources = resolver.getResources(resourcesReference)
                    if (resources.length > 0) {
                        watchedResources = (Resource[]) GrailsArrayUtils.addAll(watchedResources, resources)
                    }
                }
                catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return watchedResources
    }

    @Override
    String getDependentVersion(String name) {
        Object dependentVersion = dependencies.get(name)
        if (dependentVersion == null) {
            throw new PluginException('Plugin [' + getName() + '] referenced dependency [' + name + '] with no version!')
        }
        return dependentVersion.toString()
    }

    @Override
    String toString() {
        return '[' + getName() + ':' + getVersion() + ']'
    }

    void setWatchedResources(Resource[] watchedResources) throws IOException {
        this.@watchedResources = watchedResources
    }

    /*
     * These two properties help the closures to resolve a log and plugin variable during executing
     */
    Log getLog() {
        return LOG
    }

    GrailsPlugin getPlugin() {
        return this
    }

    void setParentApplicationContext(ApplicationContext parent) {
        // do nothing for the moment
    }

    /* (non-Javadoc)
     * @see org.grails.plugins.AbstractGrailsPlugin#refresh()
     */
    @Override
    void refresh() {
        // do nothing
        org.grails.io.support.Resource descriptor = getDescriptor()
        if (grailsApplication == null || descriptor == null) {
            return
        }

        ClassLoader parent = grailsApplication.getClassLoader()
        GroovyClassLoader gcl = new GroovyClassLoader(parent)
        try {
            initialisePlugin(gcl.parseClass(descriptor.getFile()))
        } catch (Exception e) {
            LOG.error('Error refreshing plugin: ' + e.getMessage(), e)
        }
    }

    GroovyObject getInstance() {
        return plugin
    }

    void doWithDynamicMethods(ApplicationContext ctx) {
        if (plugin instanceof Plugin) {
            ((Plugin) plugin).doWithDynamicMethods()
        }
        else {
            Object[] args = [ctx] as Object[]
            invokePluginHook(DO_WITH_DYNAMIC_METHODS, args, ctx)
        }
    }

    boolean isEnabled() {
        if (plugin instanceof Plugin) {
            return ((Plugin) plugin).enabled
        }
        else {
            return STATUS_ENABLED.equals(status)
        }
    }

    String[] getObservedPluginNames() {
        return observedPlugins
    }

    void notifyOfEvent(Map event) {
        if (plugin instanceof Plugin) {
            ((Plugin) plugin).onChange(event)
        }
        else if (onChangeListener != null) {
            invokeOnChangeListener(event)
        }
    }

    Map notifyOfEvent(int eventKind, final Object source) {
        @SuppressWarnings('unchecked')
        Map<String, Object> event = CollectionUtils.<String, Object>newMap(
            PLUGIN_CHANGE_EVENT_SOURCE, source,
            PLUGIN_CHANGE_EVENT_PLUGIN, plugin,
            PLUGIN_CHANGE_EVENT_APPLICATION, grailsApplication,
            PLUGIN_CHANGE_EVENT_MANAGER, getManager(),
            PLUGIN_CHANGE_EVENT_CTX, applicationContext)

        switch (eventKind) {
            case EVENT_ON_CHANGE:
                if (plugin instanceof Plugin) {
                    ((Plugin) plugin).onChange(event)
                }
                else {
                    notifyOfEvent(event)
                }
                getManager().informObservers(getName(), event)
                break
            case EVENT_ON_SHUTDOWN:
                if (plugin instanceof Plugin) {
                    ((Plugin) plugin).onShutdown(event)
                }
                else {
                    invokeOnShutdownEventListener(event)
                }
                break

            case EVENT_ON_CONFIG_CHANGE:
                if (plugin instanceof Plugin) {
                    ((Plugin) plugin).onConfigChange(event)
                }
                else {

                    invokeOnConfigChangeListener(event)
                }
                break
            default:
                notifyOfEvent(event)
        }

        return event
    }

    private void invokeOnShutdownEventListener(Map event) {
        callEvent(onShutdownListener, event)
    }

    private void invokeOnConfigChangeListener(Map event) {
        callEvent(onConfigChangeListener, event)
    }

    private void callEvent(Closure closureHook, Map event) {
        if (closureHook == null) {
            return
        }

        closureHook.setDelegate(this)
        closureHook.call([event] as Object[])
    }

    private void invokeOnChangeListener(Map event) {
        onChangeListener.setDelegate(this)
        onChangeListener.call([event] as Object[])

        if (!(applicationContext instanceof GenericApplicationContext)) {
            return
        }

        // Apply any factory post processors in case the change listener has changed any
        // bean definitions (GRAILS-5763)
        GenericApplicationContext ctx = (GenericApplicationContext) applicationContext
        ConfigurableListableBeanFactory beanFactory = ctx.getBeanFactory()
        for (BeanFactoryPostProcessor postProcessor in ctx.getBeanFactoryPostProcessors()) {
            try {
                postProcessor.postProcessBeanFactory(beanFactory)
            } catch (IllegalStateException e) {
                // post processor doesn't allow running again, just continue
            }
        }
    }

    void doArtefactConfiguration() {
        if (!pluginBean.isReadableProperty(ARTEFACTS)) {
            return
        }

        List l
        if (plugin instanceof Plugin) {
            l = ((Plugin) plugin).getArtefacts()
        }
        else {

            l = (List) plugin.getProperty(ARTEFACTS)
        }
        for (Object artefact in l) {
            if (artefact instanceof Class) {
                Class artefactClass = (Class) artefact
                if (ArtefactHandler.isAssignableFrom(artefactClass)) {
                    try {
                        grailsApplication.registerArtefactHandler((ArtefactHandler) artefactClass.getDeclaredConstructor().newInstance())
                    }
                    catch (InstantiationException | InvocationTargetException | NoSuchMethodException e) {
                        LOG.error('Cannot instantiate an Artefact Handler:' + e.getMessage(), e)
                    }
                    catch (IllegalAccessException e) {
                        LOG.error('The constructor of the Artefact Handler is not accessible:' + e.getMessage(), e)
                    }
                }
                else {
                    LOG.error('This class is not an ArtefactHandler:' + artefactClass.getName())
                }
            }
            else if (artefact instanceof ArtefactHandler) {
                grailsApplication.registerArtefactHandler((ArtefactHandler) artefact)
            }
            else {
                LOG.error('This object is not an ArtefactHandler:' + artefact + '[' + artefact.getClass().getName() + ']')
            }
        }
    }

    Class<?>[] getProvidedArtefacts() {
        return providedArtefacts
    }

    List<String> getPluginExcludes() {
        return pluginExcludes
    }

    Collection<? extends TypeFilter> getTypeFilters() {
        return typeFilters
    }

    String getFullName() {
        return getName() + '-' + getVersion()
    }

    org.grails.io.support.Resource getDescriptor() {
        return new SpringResource(pluginDescriptor)
    }

    void setDescriptor(Resource descriptor) {
        pluginDescriptor = descriptor
    }

    org.grails.io.support.Resource getPluginDir() {
        try {
            return new SpringResource(pluginDescriptor.createRelative('.'))
        }
        catch (IOException e) {
            return null
        }
    }

    Map getProperties() {
        return DefaultGroovyMethods.getProperties(plugin)
    }

}
