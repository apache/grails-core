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
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

import org.jspecify.annotations.Nullable;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.util.StringUtils;

import grails.core.GrailsApplication;
import grails.io.IOUtils;
import grails.io.ResourceUtils;
import grails.plugins.exceptions.PluginException;
import grails.util.BuildSettings;
import org.apache.grails.core.plugins.PluginDescriptor;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.core.io.StaticResourceLoader;

/**
 * A pre-compiled plugin from a jar or a class directory, described by the
 * {@code META-INF/grails/plugins/<name>/plugin.properties} its compiler wrote, with the artefacts and
 * precompiled views it ships.
 *
 * @see grails.plugins.GrailsPlugin
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public class BinaryGrailsPlugin extends DefaultGrailsPlugin {

    public static final String VIEWS_PROPERTIES = "views.properties";
    public static final char UNDERSCORE = '_';
    public static final String PROPERTIES_EXTENSION = ".properties";
    public static final String DEFAULT_PROPERTIES_ENCODING = "UTF-8";

    private final PluginDescriptor descriptor;
    private Class<?>[] providedArtefacts = {};
    private final Map<String, Class> precompiledViewMap = new HashMap<>();
    private final @Nullable Resource baseResource;
    private final @Nullable Resource baseResourcesResource;
    private final boolean isJar;
    private final @Nullable File projectDirectory;

    /**
     * A plugin from a jar or a class directory, described by its descriptor.
     *
     * @param pluginClass the class the descriptor names
     * @param descriptor the plugin's descriptor
     * @param application the Grails application
     */
    public BinaryGrailsPlugin(Class<?> pluginClass, PluginDescriptor descriptor, GrailsApplication application) {
        super(pluginClass, descriptor, application);
        this.descriptor = descriptor;
        URL rootResource = IOUtils.findRootResource(pluginClass);
        this.baseResource = rootResource != null ? new UrlResource(rootResource) : null;
        this.isJar = resolveIsJar(this.baseResource);
        this.projectDirectory = (!isJar && rootResource != null) ? IOUtils.findApplicationDirectoryFile(pluginClass) : null;
        if (BuildSettings.BASE_DIR != null && projectDirectory != null) {
            try {
                if (projectDirectory.getCanonicalPath().startsWith(BuildSettings.BASE_DIR.getCanonicalPath())) {
                    isBase = true;
                }
            }
            catch (IOException e) {
                // ignore
            }
        }
        URL rootResourcesURL = IOUtils.findRootResourcesURL(pluginClass);
        this.baseResourcesResource = rootResourcesURL != null ? new UrlResource(rootResourcesURL) : null;
        this.providedArtefacts = loadClasses(descriptor.getProvidedClasses());
        initializeViewMap();
    }

    @Override
    public void refresh() {
        // Binary plugins are pre-compiled and cannot be re-parsed from their descriptor resource
    }

    private static boolean resolveIsJar(@Nullable Resource baseResource) {
        if (baseResource == null) {
            return true;
        }
        try {
            return ResourceUtils.isJarURL(baseResource.getURL());
        }
        catch (IOException e) {
            return true;
        }
    }

    public @Nullable File getProjectDirectory() {
        return projectDirectory;
    }

    /**
     * Reads the plugin's precompiled views from {@code META-INF/grails/plugins/<name>/views.properties}.
     */
    protected void initializeViewMap() {
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

    @Override
    public Class<?>[] getProvidedArtefacts() {
        return providedArtefacts;
    }

    /**
     * @return the parsed plugin descriptor
     */
    public PluginDescriptor getPluginDescriptor() {
        return descriptor;
    }

    /**
     * Resolves a static resource contained within this binary plugin.
     *
     * @param path the relative path to the static resource
     * @return the resource or {@code null} if it doesn't exist
     */
    public @Nullable Resource getResource(String path) {
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        final Resource descriptorResource = descriptor.getResource();
        if (descriptorResource != null) {
            try {
                Resource resource = descriptorResource.createRelative("static" + normalizedPath);
                if (resource != null && resource.exists()) {
                    return resource;
                }
            } catch (IOException e) {
                // fall through
            }
        }
        if (baseResource != null) {
            try {
                Resource metaInfStatic = baseResource.createRelative("META-INF/static" + normalizedPath);
                if (metaInfStatic != null && metaInfStatic.exists()) {
                    return metaInfStatic;
                }
                Resource resource = baseResource.createRelative("static" + normalizedPath);
                if (resource != null && resource.exists()) {
                    return resource;
                }
            } catch (IOException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Obtains all properties for this binary plugin for the given locale.
     *
     * <p>Note this method does not cache so clients should in general cache the results of this method.</p>
     *
     * @param locale the locale
     * @return the properties or {@code null} if none exist
     */
    public @Nullable Properties getProperties(final Locale locale) {
        Resource url = this.baseResourcesResource;
        Properties properties = null;
        if (url != null) {
            StaticResourceLoader resourceLoader = new StaticResourceLoader();
            resourceLoader.setBaseResource(url);
            ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(resourceLoader);
            try {
                Resource[] resources = resolver.getResources('*' + PROPERTIES_EXTENSION);
                resources = resources.length > 0 ? filterResources(resources, locale) : resources;
                if (resources.length > 0) {
                    properties = new Properties();

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
        List<Resource> finalResources = new ArrayList<>(resources.length);

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
            try (InputStream inputStream = messageResource.getInputStream()) {
                properties.load(new InputStreamReader(
                        inputStream, Charset.forName(System.getProperty("file.encoding", DEFAULT_PROPERTIES_ENCODING))));
            }
        }
    }

    /**
     * Resolves a view for the given view name.
     *
     * @param viewName the view name
     * @return the view class which is a subclass of GroovyPage
     */
    public Class resolveView(String viewName) {
        String extraPath = "/plugins/" + getName() + versionSuffix() + '/';
        viewName = viewName.replace(extraPath, "/");

        return precompiledViewMap.get(viewName);
    }
}
