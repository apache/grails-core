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
package grails.boot.config.tools

import java.lang.annotation.Annotation

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.transform.Memoized
import groovy.util.logging.Slf4j

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.ResourcePatternResolver
import org.springframework.util.ClassUtils

import grails.config.Settings
import grails.io.IOUtils
import org.apache.grails.common.compiler.asm.AnnotationMetadataReader

/**
 * Used to scan for classes on the classpath in the most efficient manner possible.
 *
 * WARNING: Classpath scanning can be expensive! Use with care.
 *
 * @author Graeme Rocher
 * @since 3.1.12
 */
@Slf4j
@CompileStatic
class ClassPathScanner {
    private static final List DEFAULT_IGNORED_ROOT_PACKAGES = ['com', 'org', 'net', 'co', 'java', 'javax', 'jakarta', 'groovy']


    /**
     * Scans for classes relative to the given class
     *
     * @param applicationClass The class, usually the Application class
     *
     * @return A set of classes
     */
    Set<Class> scan(Class applicationClass) {
        return scan(applicationClass,[applicationClass.package.name])
    }

    /**
     * Scans for classes relative to the given class
     *
     * @param applicationClass The class, usually the Application class
     * @param annotationFilter The annotation to filter by
     *
     * @return A set of classes
     */
    Set<Class> scan(Class applicationClass, Class<? extends Annotation> annotationFilter) {
        return scan(applicationClass,[applicationClass.package.name], annotationFilter)
    }

    /**
     * Scans for classes relative to the given class
     *
     * @param applicationClass The class, usually the Application class
     * @param annotationFilter The annotation to filter by
     *
     * @return A set of classes
     */
    Set<Class> scan(Class applicationClass, Closure<Boolean> annotationFilter ) {
        return scan(applicationClass,[applicationClass.package.name], annotationFilter)
    }
    /**
     * Scans for classes relative to the given class
     *
     * @param applicationClass The class, usually the Application class
     * @param packageNames The package names to scan
     * @param annotationFilter The annotation to filter by
     *
     * @return A set of classes
     */
    Set<Class> scan(Class applicationClass, Collection<String> packageNames, Class<? extends Annotation> annotationFilter) {
        ResourcePatternResolver resourcePatternResolver = new GrailsClasspathIgnoringResourceResolver(applicationClass)

        return scan(applicationClass.getClassLoader(), resourcePatternResolver, packageNames, { String annotation ->
            annotationFilter.name == annotation
        })
    }

    /**
     * Scans for classes relative to the given class
     *
     * @param applicationClass The class, usually the Application class
     * @param packageNames The package names to scan
     * @return A set of classes
     */
    Set<Class> scan(Class applicationClass, Collection<String> packageNames, Closure<Boolean> annotationFilter = { String annotation -> annotation.startsWith('grails.') }) {
        ResourcePatternResolver resourcePatternResolver = new GrailsClasspathIgnoringResourceResolver(applicationClass)
        return scan(applicationClass.getClassLoader(), resourcePatternResolver,packageNames, annotationFilter)
    }

    /**
     * Scans for classes in the given package names
     *
     * @param resourcePatternResolver The resolver to use
     * @param packageNames The package names
     * @return The found classes
     */
    Set<Class> scan(ResourcePatternResolver resourcePatternResolver, Collection<String> packageNames, Closure<Boolean> annotationFilter = { String annotation -> annotation.startsWith('grails.') }) {
        ClassLoader classLoader = resourcePatternResolver.getClassLoader()
        return scan(classLoader, resourcePatternResolver, packageNames, annotationFilter)
    }

    /**
     * Scans for classes in the given package names
     *
     * @param classLoader The classloader to use to load classes
     * @param resourcePatternResolver The resolver
     * @param packageNames The package names
     * @param annotationFilter The filter
     * @return The classes
     */
    Set<Class> scan(ClassLoader classLoader, ResourcePatternResolver resourcePatternResolver, Collection<String> packageNames, Closure<Boolean> annotationFilter = { String annotation -> annotation.startsWith('grails.') }) {
        Set<Class> classes = []
        for (String pkg in packageNames.unique()) {
            if (pkg == null) continue
            if (ignoredRootPackages().contains(pkg)) {
                continue
            }

            if(pkg == "") {
                // try the default package in case of a script without recursing into subpackages
                log.warn("The application defines a Groovy source using the default package. Please move all Groovy sources into a package.")
                String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +  "*.class"
                scanUsingPattern(resourcePatternResolver, pattern, classLoader, annotationFilter, classes)
            }
            else {
                String pattern = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                        ClassUtils.convertClassNameToResourcePath(pkg) + Settings.CLASS_RESOURCE_PATTERN;

                scanUsingPattern(resourcePatternResolver, pattern, classLoader, annotationFilter, classes)
            }
        }
        return classes
    }

    /**
     * Whether the given resource is excluded
     *
     * @param res The resource
     * @return True if it should be excluded
     */
    protected boolean isExcluded(Resource res) {
        String filename = res.filename
        return filename.contains('$') || filename.startsWith("gsp_") || filename.endsWith("_gson.class")
    }

    /**
     * @return The root packages to ignore by default
     */
    protected List ignoredRootPackages() {
        DEFAULT_IGNORED_ROOT_PACKAGES
    }

    private void scanUsingPattern(ResourcePatternResolver resourcePatternResolver, String pattern, ClassLoader classLoader, Closure<Boolean> annotationFilter, Set<Class> classes) {
        def resources = resourcePatternResolver.getResources(pattern)
        for (Resource res in resources) {
            // ignore closures / inner classes
            if (!isExcluded(res)) {
                def reader = new AnnotationMetadataReader(res, classLoader)
                def metadata = reader.annotationMetadata
                if (metadata.annotationTypes.any(annotationFilter)) {
                    classes << classLoader.loadClass(reader.classMetadata.className)
                }
            }
        }
    }


    @CompileStatic
    @InheritConstructors
    @Slf4j
    private static class GrailsClasspathIgnoringResourceResolver extends PathMatchingResourcePatternResolver {

        GrailsClasspathIgnoringResourceResolver(Class applicationClass) {
            super(new DefaultResourceLoader(new ApplicationRelativeClassLoader(applicationClass)))
        }

        @Memoized(maxCacheSize = 20)
        protected Set<Resource> doFindAllClassPathResources(String path) throws IOException {
            Set<Resource> result = new LinkedHashSet<Resource>(16)
            ClassLoader cl = getClassLoader()
            Enumeration<URL> resourceUrls = (cl != null ? cl.getResources(path) : ClassLoader.getSystemResources(path))
            while (resourceUrls.hasMoreElements()) {

                URL url = resourceUrls.nextElement()
                // if the path is from a JAR file ignore, plugins inside JAR files will have their own mechanism for loading
                if(!url.path.contains('jar!/grails/')) {
                    result.add(convertClassLoaderURL(url))
                }

            }
            return result
        }

        @Memoized
        protected Resource[] findAllClassPathResources(String location) throws IOException {
            return super.findAllClassPathResources(location)
        }

        @Memoized
        protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
            return super.findPathMatchingResources(locationPattern)
        }

    }

    private static class ApplicationRelativeClassLoader extends URLClassLoader {

        final URL rootResource
        final Class applicationClass
        final boolean jarDeployed

        ApplicationRelativeClassLoader(Class applicationClass) {
            super([IOUtils.findRootResource(applicationClass)] as URL[])

            this.rootResource = getURLs()[0]
            this.applicationClass = applicationClass
            String urlStr = rootResource.toString()
            jarDeployed = urlStr.startsWith("jar:")
            try {
                URL withoutBang = new URL("${urlStr.substring(0, urlStr.length() - 2)}/")
                addURL(withoutBang)

            } catch (MalformedURLException e) {
                // ignore, running as a WAR
            }
        }

        @Override
        Enumeration<URL> getResources(String name) throws IOException {
            if(jarDeployed && name == '') {
                return applicationClass.getClassLoader().getResources(name)
            }
            else {
                return super.findResources(name)
            }
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try {
                return super.loadClass(name, resolve)
            } catch (ClassNotFoundException cnfe) {
                return applicationClass.getClassLoader().loadClass(name)
            }
        }
    }
}
