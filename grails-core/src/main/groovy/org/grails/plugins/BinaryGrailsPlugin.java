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
import org.apache.grails.core.plugins.PluginMetadata;
import org.apache.grails.core.plugins.PluginUtils;
import org.grails.core.io.StaticResourceLoader;

/**
 * Models a pre-compiled binary plugin backed by a {@code META-INF/grails/plugins/<name>/plugin.properties}
 * descriptor, with or without a Groovy/Java plugin descriptor class.
 *
 * @see grails.plugins.GrailsPlugin
 *
 * @author Graeme Rocher
 * @since 2.0
 */
@SuppressWarnings("rawtypes")
public class BinaryGrailsPlugin extends DefaultGrailsPlugin {

    /**
     * Legacy XML plugin descriptor path.
     *
     * @deprecated Replaced in Grails 9.0 by {@link PluginUtils#PLUGINS_IMPORTS_PATH} and
     *             {@code META-INF/grails/plugins/<name>/plugin.properties}.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    @SuppressWarnings("removal")
    public static final String PLUGIN_DESCRIPTOR_PATH = PluginUtils.LEGACY_PLUGIN_DESCRIPTOR_PATH;

    public static final String VIEWS_PROPERTIES = "views.properties";
    public static final String RELATIVE_VIEWS_PROPERTIES = "gsp/views.properties";
    public static final char UNDERSCORE = '_';
    public static final String PROPERTIES_EXTENSION = ".properties";
    public static final String DEFAULT_PROPERTIES_ENCODING = "UTF-8";

    private final @Nullable PluginDescriptor descriptor;
    private Class[] providedArtefacts = {};
    private final Map<String, Class> precompiledViewMap = new HashMap<>();
    private final @Nullable Resource baseResource;
    private final @Nullable Resource baseResourcesResource;
    private final boolean isJar;
    private final @Nullable File projectDirectory;

    /**
     * Creates a binary plugin instance backed by a plugin descriptor class.
     *
     * @param pluginClass the plugin class
     * @param descriptor the parsed plugin descriptor
     * @param application the application
     */
    public BinaryGrailsPlugin(Class<?> pluginClass, @Nullable PluginDescriptor descriptor, GrailsApplication application) {
        super(pluginClass,
                descriptor != null ? descriptor.getResource() : null,
                application,
                descriptor != null ? descriptor.getPluginVersion() : null,
                descriptor != null && descriptor.getProvidedPlugins().size() <= 1 ? descriptor.getPluginName() : null);
        this.descriptor = descriptor;
        URL rootResource = IOUtils.findRootResource(pluginClass);
        this.baseResource = rootResource != null ? new UrlResource(rootResource) : null;
        this.isJar = resolveIsJar(this.baseResource, descriptor, pluginClass);
        this.projectDirectory = (!isJar && rootResource != null) ? IOUtils.findApplicationDirectoryFile(pluginClass) : null;

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
        this.baseResourcesResource = rootResourcesURL != null ? new UrlResource(rootResourcesURL) : null;
        if (descriptor != null) {
            initializeProvidedArtefacts(descriptor.getProvidedClasses());
            initializeViewMap(descriptor);
        }
    }

    /**
     * Creates a binary plugin instance for a descriptor-less starter plugin that declares no plugin class.
     *
     * @param descriptor the parsed plugin descriptor
     * @param metadata the extracted plugin metadata
     * @param application the application
     * @since 9.0
     */
    public BinaryGrailsPlugin(PluginDescriptor descriptor, PluginMetadata metadata, GrailsApplication application) {
        super(metadata, descriptor.getResource(), application);
        this.descriptor = descriptor;
        Resource archiveRoot = resolveArchiveRootResource(descriptor.getResource());
        this.baseResource = archiveRoot != null ? archiveRoot : descriptor.getResource();
        this.isJar = resolveIsJar(this.baseResource, descriptor, null);
        this.projectDirectory = null;
        this.baseResourcesResource = archiveRoot;
        initializeProvidedArtefacts(descriptor.getProvidedClasses());
        initializeViewMap(descriptor);
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static @Nullable Resource resolveArchiveRootResource(@Nullable Resource descriptorResource) {
        if (descriptorResource == null) {
            return null;
        }
        try {
            String urlStr = descriptorResource.getURL().toString();
            int markerIdx = urlStr.lastIndexOf(PluginUtils.PLUGIN_DESCRIPTORS_PREFIX);
            if (markerIdx < 0) {
                markerIdx = urlStr.lastIndexOf(PluginUtils.LEGACY_PLUGIN_DESCRIPTOR_PATH);
            }
            if (markerIdx > 0) {
                return new UrlResource(urlStr.substring(0, markerIdx));
            }
        } catch (IOException e) {
            // fall through
        }
        return null;
    }

    @Override
    public void refresh() {
        // Binary plugins are pre-compiled and cannot be re-parsed from their descriptor resource
    }

    private static boolean resolveIsJar(
            @Nullable Resource baseResource,
            @Nullable PluginDescriptor descriptor,
            @Nullable Class<?> pluginClass) {
        if (baseResource != null) {
            try {
                return ResourceUtils.isJarURL(baseResource.getURL());
            } catch (IOException e) {
                if (descriptor == null && pluginClass != null) {
                    throw new PluginException("Cannot evaluate plugin location for plugin " + pluginClass, e);
                }
                return true;
            }
        }
        return true;
    }

    public @Nullable File getProjectDirectory() {
        return projectDirectory;
    }

    protected void initializeViewMap(PluginDescriptor descriptor) {
        Resource viewsPropertiesResource = resolveViewsPropertiesResource(descriptor);
        if (viewsPropertiesResource == null || !viewsPropertiesResource.exists()) {
            return;
        }

        Properties viewsProperties = new Properties();
        try (InputStream input = viewsPropertiesResource.getInputStream()) {
            viewsProperties.load(input);
            for (Object view : viewsProperties.keySet()) {
                String viewName = view.toString();
                final String viewClassName = viewsProperties.getProperty(viewName);
                try {
                    final Class<?> viewClass = grailsApplication.getClassLoader().loadClass(viewClassName);
                    precompiledViewMap.put(viewName, viewClass);
                } catch (Throwable e) {
                    throw new PluginException(
                            "Failed to initialize view [" + viewName + "] from plugin [" + getName() + "] : " + e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            LOG.error("Error loading views for binary plugin [" + this + "]: " + e.getMessage(), e);
        }
    }

    private @Nullable Resource resolveViewsPropertiesResource(PluginDescriptor descriptor) {
        ClassLoader classLoader = grailsApplication != null ? grailsApplication.getClassLoader() : null;
        if (classLoader != null) {
            URL namespacedViews = classLoader.getResource(PluginUtils.getPluginViewsPath(getName()));
            if (namespacedViews != null) {
                return new UrlResource(namespacedViews);
            }
        }

        final Resource descriptorResource = descriptor.getResource();
        if (descriptorResource != null) {
            try {
                Resource relative = descriptorResource.createRelative(VIEWS_PROPERTIES);
                if (relative != null && relative.exists()) {
                    return relative;
                }
            } catch (IOException e) {
                // ignore
            }
        }

        if (baseResource != null) {
            try {
                Resource rootViews = baseResource.createRelative(RELATIVE_VIEWS_PROPERTIES);
                if (rootViews != null && rootViews.exists()) {
                    return rootViews;
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return null;
    }

    protected void initializeProvidedArtefacts(List<String> classNames) {
        List<Class> artefacts = new ArrayList<>();
        if (!classNames.isEmpty()) {
            final ClassLoader classLoader = grailsApplication.getClassLoader();
            for (String className : classNames) {
                try {
                    artefacts.add(classLoader.loadClass(className));
                } catch (Throwable e) {
                    throw new PluginException(
                            "Failed to initialize class [" + className + "] from plugin [" + getName() + "] : " + e.getMessage(), e);
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
     * @return the parsed plugin descriptor
     */
    public @Nullable PluginDescriptor getPluginDescriptor() {
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
        final Resource descriptorResource = descriptor != null ? descriptor.getResource() : null;
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
        String extraPath = "/plugins/" + getName() + '-' + getVersion() + '/';
        viewName = viewName.replace(extraPath, "/");

        return precompiledViewMap.get(viewName);
    }
}
