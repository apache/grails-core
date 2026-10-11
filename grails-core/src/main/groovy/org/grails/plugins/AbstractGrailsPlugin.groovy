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

import groovy.transform.CompileStatic
import groovy.transform.stc.POJO
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.BeansException
import org.springframework.context.ApplicationContext
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.PropertySource
import org.springframework.util.Assert

import grails.config.Config
import grails.core.GrailsApplication
import grails.plugins.GrailsPlugin
import grails.plugins.GrailsPluginManager
import grails.util.GrailsNameUtils
import org.apache.grails.core.plugins.PluginUtils
import org.grails.core.AbstractGrailsClass
import org.grails.plugins.support.WatchPattern

/**
 * Abstract implementation that provides some default behaviours
 *
 * @author Graeme Rocher
 */
@CompileStatic
abstract class AbstractGrailsPlugin extends GroovyObjectSupport implements GrailsPlugin {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGrailsPlugin)

    public static final String PLUGIN_YML = PluginUtils.PLUGIN_YML_CONFIG
    public static final String PLUGIN_YML_PATH = PluginUtils.PLUGIN_YML_CONFIG_PATH
    public static final String PLUGIN_GROOVY = PluginUtils.PLUGIN_GROOVY_CONFIG
    public static final String PLUGIN_GROOVY_PATH = PluginUtils.PLUGIN_GROOVY_CONFIG_PATH

    protected GrailsApplication grailsApplication
    protected boolean isBase = false
    protected String version = '1.0'
    protected Map<String, Object> dependencies = new HashMap<>()
    protected String[] dependencyNames = [] as String[]
    protected Class<?> pluginClass
    protected ApplicationContext applicationContext
    protected GrailsPluginManager manager
    protected String[] evictionList = [] as String[]
    protected Config config

    /**
     * Wrapper Grails class for plugins.
     *
     * @author Graeme Rocher
     */
    @POJO
    @CompileStatic
    static class GrailsPluginClass extends AbstractGrailsClass {
        GrailsPluginClass(Class<?> clazz) {
            super(clazz, TRAILING_NAME)
        }
    }

    AbstractGrailsPlugin(Class<?> pluginClass, GrailsApplication application) {
        Assert.notNull(pluginClass, 'Argument [pluginClass] cannot be null')
        Assert.isTrue(pluginClass.getName().endsWith(TRAILING_NAME),
                'Argument [pluginClass] with value [' + pluginClass +
                        "] is not a Grails plugin (class name must end with 'GrailsPlugin')")
        this.grailsApplication = application
        this.pluginClass = pluginClass
    }

    /**
     * Retrieves the plugin's property source from the Spring {@link ConfigurableEnvironment}.
     *
     * <p>Plugin configuration files ({@code plugin.yml} or {@code plugin.groovy}) are loaded
     * early in the application lifecycle by
     * {@link grails.boot.config.GrailsEnvironmentPostProcessor} and registered as named
     * property sources in the environment. This method looks up the property source by the
     * expected name ({@code "<pluginName>-plugin.yml"} or {@code "<pluginName>-plugin.groovy"}).</p>
     *
     * @return the plugin's property source, or {@code null} if no configuration was loaded
     *         or the application context is not yet available
     */
    @Override
    PropertySource<?> getPropertySource() {
        ApplicationContext mainContext = grailsApplication != null ? grailsApplication.getMainContext() : null
        if (mainContext == null) {
            return null
        }
        var environment = mainContext.getEnvironment()
        if (environment instanceof ConfigurableEnvironment configurableEnv) {
            var propertySources = configurableEnv.getPropertySources()
            String pluginName = GrailsNameUtils.getLogicalPropertyName(pluginClass.getSimpleName(), 'GrailsPlugin')
            PropertySource<?> ps = propertySources.get(pluginName + '-' + PluginUtils.PLUGIN_YML_CONFIG)
            if (ps != null) {
                return ps
            }
            return propertySources.get(pluginName + '-' + PluginUtils.PLUGIN_GROOVY_CONFIG)
        }
        return null
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPlugin#refresh()
     */
    void refresh() {
        // do nothing
    }

    @Override
    boolean isEnabled(String[] profiles) {
        return true
    }

    String getFileSystemName() {
        return getFileSystemShortName() + '-' + getVersion()
    }

    String getFileSystemShortName() {
        return GrailsNameUtils.getScriptName(getName())
    }

    Class<?> getPluginClass() {
        return pluginClass
    }

    boolean isBasePlugin() {
        return isBase
    }

    void setBasePlugin(boolean isBase) {
        this.isBase = isBase
    }

    List<WatchPattern> getWatchedResourcePatterns() {
        return Collections.emptyList()
    }

    boolean hasInterestInChange(String path) {
        return false
    }

    String[] getDependencyNames() {
        return dependencyNames
    }

    String getDependentVersion(String name) {
        return null
    }

    String getName() {
        return pluginClass.getName()
    }

    String getVersion() {
        return version
    }

    String getPluginPath() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getScriptName(getName()) + '-' + getVersion()
    }

    // https://github.com/apache/grails-core/issues/9406
    // The name of the plugin for my-plug on the path is myPlugin the GrailsNameUtils.getScriptName(getName()) will always use my-plugin
    String getPluginPathCamelCase() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(getName()) + '-' + getVersion()
    }

    GrailsPluginManager getManager() {
        return manager
    }

    String[] getLoadAfterNames() {
        return new String[0]
    }

    String[] getLoadBeforeNames() {
        return new String[0]
    }

    void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.@applicationContext = applicationContext
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPlugin#setManager(grails.plugins.GrailsPluginManager)
     */
    void setManager(GrailsPluginManager manager) {
        this.@manager = manager
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPlugin#setApplication(grails.core.GrailsApplication)
     */
    void setApplication(GrailsApplication application) {
        this.grailsApplication = application
    }

    String[] getEvictionNames() {
        return evictionList
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) return true
        if (!(o instanceof AbstractGrailsPlugin)) return false

        AbstractGrailsPlugin that = (AbstractGrailsPlugin) o

        if (!pluginClass.equals(that.pluginClass)) return false
        if (!version.equals(that.version)) return false

        return true
    }

    @Override
    int hashCode() {
        int result = version.hashCode()
        result = 31 * result + pluginClass.hashCode()
        return result
    }

    int compareTo(Object o) {
        AbstractGrailsPlugin that = (AbstractGrailsPlugin) o
        if (equals(that)) return 0

        String thatName = that.getName()
        for (String pluginName in getLoadAfterNames()) {
            if (pluginName.equals(thatName)) return -1
        }
        for (String pluginName in getLoadBeforeNames()) {
            if (pluginName.equals(thatName)) return 1
        }
        for (String pluginName in that.getLoadAfterNames()) {
            if (pluginName.equals(getName())) return 1
        }
        for (String pluginName in that.getLoadBeforeNames()) {
            if (pluginName.equals(getName())) return -1
        }

        return 0
    }

}
