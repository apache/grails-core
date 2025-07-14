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

import grails.core.GrailsApplication;
import grails.io.IOUtils;
import grails.io.ResourceUtils;
import grails.plugins.exceptions.PluginException;
import grails.util.BuildSettings;
import org.grails.core.io.StaticResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Models a pre-compiled binary plugin.
 *
 * @author Graeme Rocher
 * @see grails.plugins.GrailsPlugin
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public class BinaryGrailsPlugin extends DefaultGrailsPlugin {

    public static final String VIEWS_PROPERTIES = "views.properties";
    public static final String RELATIVE_VIEWS_PROPERTIES = "gsp/views.properties";
    public static final char UNDERSCORE = '_';
    public static final String PROPERTIES_EXTENSION = ".properties";
    public static final String DEFAULT_PROPERTIES_ENCODING = "UTF-8";
    public static final String PLUGIN_DESCRIPTOR_PATH = "META-INF/grails-plugin.xml";

    private final BinaryGrailsPluginDescriptor descriptor;
    private Class[] providedArtefacts = {};
    private final Map<String, Class> precompiledViewMap = new HashMap<String, Class>();
    private final Resource baseResource;
    private final Resource baseResourcesResource;
    private final boolean isJar;
    private final File projectDirectory;

    /**
     * Creates a binary plugin instance.
     *
     * @param pluginClass The plugin class
     * @param descriptor  The META-INF/grails-plugin.xml descriptor
     * @param application The application
     */
    public BinaryGrailsPlugin(Class<?> pluginClass, BinaryGrailsPluginDescriptor descriptor, GrailsApplication application) {
        super(pluginClass, application);
        this.descriptor = descriptor;
        URL rootResource = IOUtils.findRootResource(pluginClass);
        if (rootResource == null) {
            throw new PluginException("Cannot evaluate plugin location for plugin " + pluginClass);
        }
        this.baseResource = new UrlResource(rootResource);
        try {
            this.isJar = ResourceUtils.isJarURL(baseResource.getURL());
        } catch (IOException e) {
            throw new PluginException("Cannot evaluate plugin location for plugin " + pluginClass, e);
        }
        this.projectDirectory = isJar ? null : IOUtils.findApplicationDirectoryFile(pluginClass);

        if (BuildSettings.BASE_DIR != null && projectDirectory != null) {
            try {
                if (projectDirectory.getCanonicalPath().startsWith(BuildSettings.BASE_DIR.getCanonicalPath())) {
                    isBase = true;
                }
            } catch (IOException e) {
                // ignore
            }
        }

        URL rootResourcesURL = IOUtils.findRootResourcesURL(pluginClass);
        if (rootResourcesURL == null) {
            throw new PluginException("Cannot evaluate plugin location for plugin " + pluginClass);
        }

        this.baseResourcesResource = new UrlResource(rootResourcesURL);
        if (descriptor != null) {
            initializeProvidedArtefacts(descriptor.getProvidedlassNames());
            initializeViewMap(descriptor);
        }
    }

    public File getProjectDirectory() {
        return projectDirectory;
    }

    protected void initializeViewMap(BinaryGrailsPluginDescriptor descriptor) {
        final Resource descriptorResource = descriptor.getResource();

        Resource viewsPropertiesResource = null;
        try {
            viewsPropertiesResource = descriptorResource.createRelative(VIEWS_PROPERTIES);
        } catch (IOException e) {
            // ignore
        }

        if (viewsPropertiesResource == null || !viewsPropertiesResource.exists()) {
            try {
                String urlString = descriptorResource.getURL().toString();
                if (urlString.endsWith(PLUGIN_DESCRIPTOR_PATH)) {
                    urlString = urlString.substring(0, urlString.length() - PLUGIN_DESCRIPTOR_PATH.length());
                    URL newUrl = new URL(urlString + RELATIVE_VIEWS_PROPERTIES);
                    viewsPropertiesResource = new UrlResource(newUrl);
                }
            } catch (IOException e) {
                // ignore
            }
        }

        if (viewsPropertiesResource == null || !viewsPropertiesResource.exists()) {
            return;
        }

        Properties viewsProperties = new Properties();
        InputStream input = null;
        try {
            input = viewsPropertiesResource.getInputStream();
            viewsProperties.load(input);
            for (Object view : viewsProperties.keySet()) {
                String viewName = view.toString();
                final String viewClassName = viewsProperties.getProperty(viewName);
                try {
                    final Class<?> viewClass = grailsApplication.getClassLoader().loadClass(viewClassName);
                    precompiledViewMap.put(viewName, viewClass);
                } catch (Throwable e) {
                    throw new PluginException("Failed to initialize view [" + viewName + "] from plugin [" + getName() + "] : " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            LOG.error("Error loading views for binary plugin [" + this + "]: " + e.getMessage(), e);
        } finally {
            try {
                if (input != null) {
                    input.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
    }

    protected void initializeProvidedArtefacts(List<String> classNames) {

        List<Class> artefacts = new ArrayList<Class>();
        if (!classNames.isEmpty()) {
            final ClassLoader classLoader = grailsApplication.getClassLoader();
            for (String className : classNames) {
                try {
                    artefacts.add(classLoader.loadClass(className));
                } catch (Throwable e) {
                    throw new PluginException("Failed to initialize class [" + className + "] from plugin [" + getName() + "] : " + e.getMessage(), e);
                }

            }
        }
        artefacts.addAll(Arrays.asList(super.getProvidedArtefacts()));
        providedArtefacts = artefacts.toArray(new Class[artefacts.size()]);
    }

    @Override
    public Class<?>[] getProvidedArtefacts() {
        return providedArtefacts;
    }

    /**
     * @return The META-INF/grails-plugin.xml descriptor
     */
    public BinaryGrailsPluginDescriptor getBinaryDescriptor() {
        return descriptor;
    }

    /**
     * Resolves a static resource contained within this binary plugin
     *
     * @param path The relative path to the static resource
     * @return The resource or null if it doesn't exist
     */
    public Resource getResource(String path) {
        final Resource descriptorResource = descriptor.getResource();

        try {
            Resource resource = descriptorResource.createRelative("static" + path);
            if (resource.exists()) {
                return resource;
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Obtains all properties for this binary plugin for the given locale.
     * <p>
     * Note this method does not cache so clients should in general cache the results of this method.
     *
     * @param locale The locale
     * @return The properties or null if non exist
     */
    public Properties getProperties(final Locale locale) {
        Resource url = this.baseResourcesResource;
        Properties properties = null;
        if (url != null) {
            StaticResourceLoader resourceLoader = new StaticResourceLoader();
            resourceLoader.setBaseResource(url);
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            try {
                // first load all properties
                Resource[] resources = resolver.getResources('*' + PROPERTIES_EXTENSION);
                resources = resources.length > 0 ? filterResources(resources, locale) : resources;
                if (resources.length > 0) {
                    properties = new Properties();

                    // message bundles are locale specific. The more underscores the locale has the more specific the locale
                    // so we order by the number of underscores present so that the most specific appears
                    Arrays.sort(resources, (o1, o2) -> {
                        String f1 = o1.getFilename();
                        String f2 = o2.getFilename();

                        int firstUnderscoreCount = StringUtils.countOccurrencesOf(f1, "_");
                        int secondUnderscoreCount = StringUtils.countOccurrencesOf(f2, "_");

                        if (firstUnderscoreCount == secondUnderscoreCount) {
                            return 0;
                        } else {
                            return firstUnderscoreCount > secondUnderscoreCount ? 1 : -1;
                        }
                    });

                    loadFromResources(properties, resources);
                }
            } catch (IOException e) {
                return null;
            }
        }
        return properties;
    }

    private Resource[] filterResources(Resource[] resources, Locale locale) {
        List<Resource> finalResources = new ArrayList<Resource>(resources.length);

        for (Resource resource : resources) {
            String fn = resource.getFilename();

            if (fn.indexOf(UNDERSCORE) > -1) {
                if (fn.endsWith(UNDERSCORE + locale.toString() + PROPERTIES_EXTENSION)) {
                    finalResources.add(resource);
                } else if (fn.endsWith(UNDERSCORE + locale.getLanguage() + UNDERSCORE + locale.getCountry() + PROPERTIES_EXTENSION)) {
                    finalResources.add(resource);
                } else if (fn.endsWith(UNDERSCORE + locale.getLanguage() + PROPERTIES_EXTENSION)) {
                    finalResources.add(resource);
                }
            } else {
                finalResources.add(resource);
            }
        }
        return finalResources.toArray(new Resource[finalResources.size()]);
    }

    private void loadFromResources(Properties properties, Resource[] resources) throws IOException {
        for (Resource messageResource : resources) {
            InputStream inputStream = messageResource.getInputStream();
            try {
                properties.load(new InputStreamReader(inputStream, Charset.forName(System.getProperty("file.encoding", DEFAULT_PROPERTIES_ENCODING))));
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    /**
     * Resolves a view for the given view name.
     *
     * @param viewName The view name
     * @return The view class which is a subclass of GroovyPage
     */
    public Class resolveView(String viewName) {

        // this is a workaround for GRAILS-9234; in that scenario the viewName will be
        // "/WEB-INF/grails-app/views/plugins/plugin9234-0.1/junk/_book.gsp" with the
        // extra "/plugins/plugin9234-0.1". I'm not sure if that's needed elsewhere, so
        // removing it here for the lookup
        String extraPath = "/plugins/" + getName() + '-' + getVersion() + '/';
        viewName = viewName.replace(extraPath, "/");

        return precompiledViewMap.get(viewName);
    }
}
