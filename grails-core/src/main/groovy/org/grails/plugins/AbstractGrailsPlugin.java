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

import grails.config.Config;
import grails.core.GrailsApplication;
import grails.io.IOUtils;
import grails.plugins.GrailsPlugin;
import grails.plugins.GrailsPluginManager;
import grails.util.GrailsNameUtils;
import groovy.lang.GroovyObjectSupport;
import org.grails.config.yaml.YamlPropertySourceLoader;
import org.grails.core.AbstractGrailsClass;
import org.grails.core.cfg.GroovyConfigPropertySourceLoader;
import org.grails.plugins.support.WatchPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.util.Assert;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract implementation that provides some default behaviours
 *
 * @author Graeme Rocher
 */
public abstract class AbstractGrailsPlugin extends GroovyObjectSupport implements GrailsPlugin {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractGrailsPlugin.class);

    public static final String PLUGIN_YML = "plugin.yml";
    public static final String PLUGIN_YML_PATH = "/" + PLUGIN_YML;
    public static final String PLUGIN_GROOVY = "plugin.groovy";
    public static final String PLUGIN_GROOVY_PATH = "/" + PLUGIN_GROOVY;
    private static final List<String> DEFAULT_CONFIG_IGNORE_LIST = Arrays.asList("dataSource", "hibernate");
    private static Resource basePluginResource = null;
    protected PropertySource<?> propertySource;
    protected GrailsApplication grailsApplication;
    protected boolean isBase = false;
    protected String version = "1.0";
    protected Map<String, Object> dependencies = new HashMap<String, Object>();
    protected String[] dependencyNames = {};
    protected Class<?> pluginClass;
    protected ApplicationContext applicationContext;
    protected GrailsPluginManager manager;
    protected String[] evictionList = {};
    protected Config config;

    /**
     * Wrapper Grails class for plugins.
     *
     * @author Graeme Rocher
     */
    class GrailsPluginClass extends AbstractGrailsClass {
        public GrailsPluginClass(Class<?> clazz) {
            super(clazz, TRAILING_NAME);
        }
    }

    public AbstractGrailsPlugin(Class<?> pluginClass, GrailsApplication application) {
        Assert.notNull(pluginClass, "Argument [pluginClass] cannot be null");
        Assert.isTrue(pluginClass.getName().endsWith(TRAILING_NAME),
                "Argument [pluginClass] with value [" + pluginClass +
                        "] is not a Grails plugin (class name must end with 'GrailsPlugin')");
        this.grailsApplication = application;
        this.pluginClass = pluginClass;
        Resource resource = readPluginConfiguration(pluginClass);

        if(resource != null && resource.exists()) {
            final String filename = resource.getFilename();
            try {
                if (filename.equals(PLUGIN_YML)) {
                    YamlPropertySourceLoader propertySourceLoader = new YamlPropertySourceLoader();
                    this.propertySource = propertySourceLoader.load(GrailsNameUtils.getLogicalPropertyName(pluginClass.getSimpleName(), "GrailsPlugin") + "-" + PLUGIN_YML, resource, DEFAULT_CONFIG_IGNORE_LIST).stream().findFirst().orElse(null);
                } else if (filename.equals(PLUGIN_GROOVY)) {
                    GroovyConfigPropertySourceLoader propertySourceLoader = new GroovyConfigPropertySourceLoader();
                    this.propertySource = propertySourceLoader.load(GrailsNameUtils.getLogicalPropertyName(pluginClass.getSimpleName(), "GrailsPlugin") + "-" + PLUGIN_GROOVY, resource, DEFAULT_CONFIG_IGNORE_LIST).stream().findFirst().orElse(null);
                }
            } catch (IOException e) {
                LOG.warn("Error loading " + filename + " for plugin: " + pluginClass.getName() +": " + e.getMessage(), e);
            }
        }
    }

    @Override
    public PropertySource<?> getPropertySource() {
        return propertySource;
    }

    /* (non-Javadoc)
                 * @see grails.plugins.GrailsPlugin#refresh()
                 */
    public void refresh() {
        // do nothing
    }


    @Override
    public boolean isEnabled(String[] profiles) {
        return true;
    }

    protected Resource readPluginConfiguration(Class<?> pluginClass) {
        Resource ymlResource = getConfigurationResource(pluginClass, PLUGIN_YML_PATH);
        Resource groovyResource = getConfigurationResource(pluginClass, PLUGIN_GROOVY_PATH);

        Boolean groovyResourceExists = groovyResource != null && groovyResource.exists();

        if(ymlResource != null && ymlResource.exists()) {
            if (groovyResourceExists) {
                throw new RuntimeException("A plugin [" + pluginClass.getName() + "] may define a plugin.yml or a plugin.groovy, but not both");
            }
            return ymlResource;
        }
        if(groovyResourceExists) {
            return groovyResource;
        }
        return null;
    }

    protected Resource getConfigurationResource(Class<?> pluginClass, String path) {
        final URL urlToConfig = IOUtils.findResourceRelativeToClass(pluginClass, path);
        return urlToConfig != null ? new UrlResource(urlToConfig) : null;
    }

    public String getFileSystemName() {
        return getFileSystemShortName() + '-' + getVersion();
    }

    public String getFileSystemShortName() {
        return GrailsNameUtils.getScriptName(getName());
    }

    public Class<?> getPluginClass() {
        return pluginClass;
    }

    public boolean isBasePlugin() {
        return isBase;
    }

    public void setBasePlugin(boolean isBase) {
        this.isBase = isBase;
    }

    public List<WatchPattern> getWatchedResourcePatterns() {
        return Collections.emptyList();
    }

    public boolean hasInterestInChange(String path) {
        return false;
    }

    public boolean checkForChanges() {
        return false;
    }


    public String[] getDependencyNames() {
        return dependencyNames;
    }

    public String getDependentVersion(String name) {
        return null;
    }

    public String getName() {
        return pluginClass.getName();
    }

    public String getVersion() {
        return version;
    }

    public String getPluginPath() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getScriptName(getName()) + '-' + getVersion();
    }

    // https://github.com/apache/grails-core/issues/9406
    // The name of the plugin for my-plug on the path is myPlugin the GrailsNameUtils.getScriptName(getName()) will always use my-plugin
    public String getPluginPathCamelCase() {
        return PLUGINS_PATH + '/' + GrailsNameUtils.getPropertyNameForLowerCaseHyphenSeparatedName(getName()) + '-' + getVersion();
    }

    public GrailsPluginManager getManager() {
        return manager;
    }

    public String[] getLoadAfterNames() {
        return new String[0];
    }

    public String[] getLoadBeforeNames() {
        return new String[0];
    }

    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPlugin#setManager(grails.plugins.GrailsPluginManager)
     */
    public void setManager(GrailsPluginManager manager) {
        this.manager = manager;
    }

    /* (non-Javadoc)
     * @see grails.plugins.GrailsPlugin#setApplication(grails.core.GrailsApplication)
     */
    public void setApplication(GrailsApplication application) {
        this.grailsApplication = application;
    }

    public String[] getEvictionNames() {
        return evictionList;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractGrailsPlugin)) return false;

        AbstractGrailsPlugin that = (AbstractGrailsPlugin) o;

        if (!pluginClass.equals(that.pluginClass)) return false;
        if (!version.equals(that.version)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = version.hashCode();
        result = 31 * result + pluginClass.hashCode();
        return result;
    }

    public int compareTo(Object o) {
        AbstractGrailsPlugin that = (AbstractGrailsPlugin) o;
        if (equals(that)) return 0;

        String thatName = that.getName();
        for (String pluginName : getLoadAfterNames()) {
            if (pluginName.equals(thatName)) return -1;
        }
        for (String pluginName : getLoadBeforeNames()) {
            if (pluginName.equals(thatName)) return 1;
        }
        for (String pluginName : that.getLoadAfterNames()) {
            if (pluginName.equals(getName())) return 1;
        }
        for (String pluginName : that.getLoadBeforeNames()) {
            if (pluginName.equals(getName())) return -1;
        }

        return 0;
    }
}
