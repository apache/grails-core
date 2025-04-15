/*
 * Copyright 2004-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.util

import grails.io.IOUtils
import groovy.transform.CompileStatic
import org.grails.io.support.FileSystemResource
import org.grails.io.support.Resource
import org.grails.io.support.UrlResource
import org.springframework.core.env.ConfigurablePropertyResolver
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.PropertySource
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.PropertySourcesPropertyResolver
import org.springframework.core.io.InputStreamResource
import org.springframework.boot.env.YamlPropertySourceLoader
import java.lang.ref.Reference
import java.lang.ref.SoftReference

/**
 * Represents the application Metadata and loading mechanics.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
@CompileStatic
class Metadata {
    private static final long serialVersionUID = -582452926111226898L
    public static final String FILE = "application.yml"
    public static final String APPLICATION_VERSION = "info.app.version"
    public static final String APPLICATION_NAME = "info.app.name"
    public static final String DEFAULT_APPLICATION_NAME = "grailsApplication"
    public static final String APPLICATION_GRAILS_VERSION = "info.app.grailsVersion"
    public static final String SERVLET_VERSION = "info.app.servletVersion"
    public static final String WAR_DEPLOYED = "info.app.warDeployed"
    public static final String DEFAULT_SERVLET_VERSION = "6.0"
    public static final String BUILD_INFO_FILE = "META-INF/grails.build.info"

    private static Holder<Reference<Metadata>> holder = new Holder<Reference<Metadata>>("Metadata")
    private final MutablePropertySources propertySources = new MutablePropertySources()
    private final ConfigurablePropertyResolver propertyResolver
    private Resource metadataFile
    private boolean warDeployed
    private String servletVersion = DEFAULT_SERVLET_VERSION
    private Map<String, Object> props = null

    private Metadata() {
        this.propertyResolver = new PropertySourcesPropertyResolver(propertySources)
        loadFromDefault()
    }

    private Metadata(Resource res) {
        this.propertyResolver = new PropertySourcesPropertyResolver(propertySources)
        metadataFile = res
        loadFromFile(res)
    }

    private Metadata(File f) {
        this(new FileSystemResource(f))
    }

    private Metadata(InputStream inputStream) {
        this.propertyResolver = new PropertySourcesPropertyResolver(propertySources)
        loadFromInputStream(inputStream)
    }

    private Metadata(Map<String, String> properties) {
        this.propertyResolver = new PropertySourcesPropertyResolver(propertySources)
        props = new LinkedHashMap<String, Object>(properties)
        addPropertySource(new MapPropertySource("properties", props))
        afterLoading()
    }

    Resource getMetadataFile() {
        return metadataFile
    }

    static void reset() {
        Metadata m = getFromMap()
        if (m != null) {
            m.clear()
            m.afterLoading()
        }
    }

    private void afterLoading() {
        // Allow override via system properties
        addPropertySource(new MapPropertySource("systemProperties", System.getProperties() as Map))

        if (!containsKey(APPLICATION_NAME)) {
            final Map<String, Object> m = [(APPLICATION_NAME): (Object) DEFAULT_APPLICATION_NAME]
            addPropertySource(new MapPropertySource("appName", m))
        }

        warDeployed = getProperty(WAR_DEPLOYED, Boolean.class, false)
    }

    static Metadata getCurrent() {
        Metadata m = getFromMap()
        if (m == null) {
            m = new Metadata()
            holder.set(new SoftReference<Metadata>(m))
        }
        return m
    }

    private void loadFromDefault() {
        try {
            def classLoader = Thread.currentThread().getContextClassLoader()
            URL url = classLoader.getResource(FILE)
            if (url == null) {
                url = getClass().getClassLoader().getResource(FILE)
            }
            if (url != null) {
                url.withInputStream { input ->
                    addPropertySource(loadYml(input))
                }
                this.metadataFile = new UrlResource(url)
            }

            url = classLoader.getResource(BUILD_INFO_FILE)
            if (url != null) {
                if (IOUtils.isWithinBinary(url)) {
                    url.withInputStream { input ->
                        addPropertySource(loadProperties(input, "build.info"))
                    }
                }
            }
        }
        catch (Exception e) {
            throw new RuntimeException("Cannot load application metadata:" + e.getMessage(), e)
        }
    }

    private PropertySource<?> loadYml(InputStream input) {
        def loader = new YamlPropertySourceLoader()
        def resource = new InputStreamResource(input)
        def propertySources = loader.load("application", resource)
        return propertySources.isEmpty() ? null : propertySources[0]
    }

    private PropertySource<?> loadProperties(InputStream input, String name) {
        def props = new Properties()
        props.load(input)
        return new MapPropertySource(name, props as Map)
    }

    private void loadFromInputStream(InputStream inputStream) {
        addPropertySource(loadYml(inputStream))
        afterLoading()
    }

    private void loadFromFile(Resource file) {
        if (file != null && file.exists()) {
            file.inputStream.withStream { input ->
                addPropertySource(loadYml(input))
                afterLoading()
            }
        }
    }

    static Metadata getInstance(InputStream inputStream) {
        Metadata m = new Metadata(inputStream)
        holder.set(new FinalReference<Metadata>(m))
        return m
    }

    static Metadata getInstance(File file) {
        return getInstance(new FileSystemResource(file))
    }

    static Metadata getInstance(Resource file) {
        Reference<Metadata> ref = holder.get()
        if (ref != null) {
            Metadata metadata = ref.get()
            if (metadata != null && metadata.getMetadataFile()?.equals(file)) {
                return metadata
            }
        }
        return new Metadata(file)
    }

    static Metadata reload() {
        Resource f = getCurrent().getMetadataFile()
        return (f != null && f.exists()) ? getInstance(f) : new Metadata()
    }

    boolean containsKey(Object key) {
        return this.propertyResolver.containsProperty((String) key)
    }

    @Deprecated
    Object get(Object key) {
        getProperty(key.toString(), Object, null)
    }

    @Deprecated
    Object getProperty(String propertyName) {
        get(propertyName)
    }

    <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        return this.propertyResolver.getProperty(key, targetType, defaultValue)
    }

    <T> T getRequiredProperty(String key, Class<T> targetType) throws IllegalStateException {
        T value = getProperty(key, targetType, null)
        if (!value) {
            throw new IllegalStateException("Value for key [$key] cannot be resolved")
        }
        return value
    }

    Object navigate(String... path) {
        return this.propertyResolver.getProperty(path.join('.').toString(), Object, null)
    }

    String getApplicationVersion() {
        return getProperty(APPLICATION_VERSION, String.class, null)
    }

    String getGrailsVersion() {
        return getProperty(APPLICATION_GRAILS_VERSION, String.class, null)
    }

    String getEnvironment() {
        return getProperty("grails.env", String.class, null)
    }

    String getApplicationName() {
        return getProperty(APPLICATION_NAME, String.class, DEFAULT_APPLICATION_NAME)
    }

    String getServletVersion() {
        return getProperty(SERVLET_VERSION, String.class, DEFAULT_SERVLET_VERSION)
    }

    void setServletVersion(String servletVersion) {
        this.servletVersion = servletVersion
    }

    boolean isWarDeployed() {
        return warDeployed
    }

    boolean isDevelopmentEnvironmentAvailable() {
        return Environment.isDevelopmentEnvironmentAvailable()
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close()
            }
            catch (Exception ignored) {
            }
        }
    }

    private static Metadata getFromMap() {
        Reference<Metadata> metadata = holder.get()
        return metadata == null ? null : metadata.get()
    }

    void clear() {
        // Clear cached property sources
        metadataFile = null
        props = null
        loadFromDefault()
    }

    Object getOrDefault(Object key, Object defaultValue) {
        return getProperty(key.toString(), Object, defaultValue)
    }

    final static class FinalReference<T> extends SoftReference<T> {
        private final T ref

        FinalReference(T t) {
            super(t)
            ref = t
        }

        @Override
        T get() {
            return ref
        }
    }

    /**
     * Adds a new property source to the list of property sources.
     */
    private void addPropertySource(PropertySource<?> propertySource) {
        if (propertySource != null) {
            propertySources.addLast(propertySource)
        }
    }
}