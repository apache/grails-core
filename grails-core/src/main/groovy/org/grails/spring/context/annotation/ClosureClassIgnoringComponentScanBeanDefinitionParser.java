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
package org.grails.spring.context.annotation;

import grails.plugins.GrailsPluginManager;
import grails.util.BuildSettings;
import grails.util.Environment;
import grails.util.GrailsStringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.HierarchicalBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.beans.factory.xml.XmlReaderContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ComponentScanBeanDefinitionParser;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ReflectionUtils;
import org.w3c.dom.Element;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends Spring's default &lt;context:component-scan/&gt; element to ignore
 * generated classes.
 *
 * @author Graeme Rocher
 * @author Lari Hotari
 * @since 1.2
 */
public class ClosureClassIgnoringComponentScanBeanDefinitionParser extends ComponentScanBeanDefinitionParser {

    private static final Log LOG = LogFactory.getLog(ClosureClassIgnoringComponentScanBeanDefinitionParser.class);

    @Override
    protected ClassPathBeanDefinitionScanner createScanner(XmlReaderContext readerContext, boolean useDefaultFilters) {
        final ClassPathBeanDefinitionScanner scanner = super.createScanner(readerContext, useDefaultFilters);
        BeanDefinitionRegistry beanDefinitionRegistry = readerContext.getRegistry();

        GrailsPluginManager pluginManager = null;

        if (beanDefinitionRegistry instanceof HierarchicalBeanFactory) {
            HierarchicalBeanFactory beanFactory = (HierarchicalBeanFactory) beanDefinitionRegistry;
            BeanFactory parent = beanFactory.getParentBeanFactory();
            if (parent != null && parent.containsBean(GrailsPluginManager.BEAN_NAME)) {
                pluginManager = parent.getBean(GrailsPluginManager.BEAN_NAME, GrailsPluginManager.class);
            }
        }

        if (pluginManager != null) {
            List<TypeFilter> typeFilters = pluginManager.getTypeFilters();
            for (TypeFilter typeFilter : typeFilters) {
                scanner.addIncludeFilter(typeFilter);
            }
        }
        return scanner;
    }

    @Override
    protected ClassPathBeanDefinitionScanner configureScanner(ParserContext parserContext, Element element) {
        final ClassPathBeanDefinitionScanner scanner = super.configureScanner(parserContext, element);

        final ResourceLoader originalResourceLoader = parserContext.getReaderContext().getResourceLoader();
        if (LOG.isDebugEnabled()) {
            LOG.debug("Scanning only this classloader:" + originalResourceLoader.getClassLoader());
        }

        ResourceLoader parentOnlyResourceLoader;
        try {
            parentOnlyResourceLoader = new ResourceLoader() {
                ClassLoader parentOnlyGetResourcesClassLoader = new ParentOnlyGetResourcesClassLoader(originalResourceLoader.getClassLoader());

                public Resource getResource(String location) {
                    return originalResourceLoader.getResource(location);
                }

                public ClassLoader getClassLoader() {
                    return parentOnlyGetResourcesClassLoader;
                }
            };
        } catch (Throwable t) {
            // restrictive classloading environment, use the original
            parentOnlyResourceLoader = originalResourceLoader;
        }

        final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver(parentOnlyResourceLoader) {
            @Override
            protected Resource[] findAllClassPathResources(String location) throws IOException {
                Set<Resource> result = new LinkedHashSet<Resource>(16);

                if (BuildSettings.CLASSES_DIR != null) {
                    @SuppressWarnings("unused")
                    URL classesDir = BuildSettings.CLASSES_DIR.toURI().toURL();

                    // only scan classes from project classes directory
                    String path = location;
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    Enumeration<URL> resourceUrls = getClassLoader().getResources(path);
                    while (resourceUrls.hasMoreElements()) {
                        URL url = resourceUrls.nextElement();
                        if (LOG.isDebugEnabled()) {
                            LOG.debug("Scanning URL " + url.toExternalForm() + " while searching for '" + location + "'");
                        }
                    /*
                    if (!warDeployed && classesDir!= null && url.equals(classesDir)) {
                        result.add(convertClassLoaderURL(url));
                    }
                    else if (warDeployed) {
                        result.add(convertClassLoaderURL(url));
                    }
                    */
                        result.add(convertClassLoaderURL(url));
                    }
                }
                return result.toArray(new Resource[result.size()]);
            }
        };
        resourceResolver.setPathMatcher(new AntPathMatcher() {
            @Override
            public boolean match(String pattern, String path) {
                if (path.endsWith(".class")) {
                    String filename = GrailsStringUtils.getFileBasename(path);
                    if (filename.contains("$")) {
                        return false;
                    }
                }
                return super.match(pattern, path);
            }
        });
        scanner.setResourceLoader(resourceResolver);
        return scanner;
    }

    /**
     * This ClassLoader is used to restrict getResources & getResource methods only to the
     * parent ClassLoader. getResources/getResource usually search all parent level classloaders.
     * (look at details in source code of java.lang.ClassLoader.getResources)
     *
     * @author Lari Hotari
     */
    private static final class ParentOnlyGetResourcesClassLoader extends ClassLoader {

        private final Method findResourcesMethod = ReflectionUtils.findMethod(ClassLoader.class, "findResources", String.class);
        private final Method findResourceMethod = ReflectionUtils.findMethod(ClassLoader.class, "findResource", String.class);

        private ClassLoader rootLoader;

        public ParentOnlyGetResourcesClassLoader(ClassLoader parent) {
            super(parent);
            rootLoader = DefaultGroovyMethods.getRootLoader(parent);
            ReflectionUtils.makeAccessible(findResourceMethod);
            ReflectionUtils.makeAccessible(findResourcesMethod);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (Environment.isFork()) {
                return super.getResources(name);
            } else {
                if (rootLoader != null) {
                    // search all parents up to rootLoader
                    Collection<URL> urls = new LinkedHashSet<URL>();
                    findResourcesRecursive(getParent(), name, urls);
                    return Collections.enumeration(urls);
                }

                return invokeFindResources(getParent(), name);
            }
        }

        private void findResourcesRecursive(ClassLoader parent, String name, Collection<URL> urls) {
            Enumeration<URL> result = invokeFindResources(parent, name);
            while (result.hasMoreElements()) {
                urls.add(result.nextElement());
            }
            if (parent != rootLoader) {
                findResourcesRecursive(parent.getParent(), name, urls);
            }
        }

        @SuppressWarnings("unchecked")
        private Enumeration<URL> invokeFindResources(ClassLoader parent, String name) {
            return (Enumeration<URL>) ReflectionUtils.invokeMethod(findResourcesMethod, parent, name);
        }

        @Override
        public URL getResource(String name) {
            if (Environment.isFork()) {
                return super.getResource(name);
            } else {
                if (rootLoader != null) {
                    return findResourceRecursive(getParent(), name);
                }

                return invokeFindResource(getParent(), name);
            }
        }

        private URL findResourceRecursive(ClassLoader parent, String name) {
            URL url = invokeFindResource(parent, name);
            if (url != null) {
                return url;
            }

            if (parent != rootLoader) {
                return findResourceRecursive(parent.getParent(), name);
            }

            return null;
        }

        private URL invokeFindResource(ClassLoader parent, String name) {
            return (URL) ReflectionUtils.invokeMethod(findResourceMethod, parent, name);
        }
    }
}
