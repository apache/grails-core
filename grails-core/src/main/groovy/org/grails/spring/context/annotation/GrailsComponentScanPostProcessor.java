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

import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.filter.TypeFilter;
import org.springframework.util.AntPathMatcher;

import grails.plugins.GrailsPluginManager;
import grails.util.GrailsStringUtils;

/**
 * Scans the packages named by {@code grails.spring.bean.packages} for annotated components.
 *
 * <p>This is the programmatic equivalent of the {@code grailsContext:component-scan} element
 * that {@link ClosureClassIgnoringComponentScanBeanDefinitionParser} serves, so the scan can
 * be contributed without the XML namespace handler and therefore without the bean builder
 * DSL. The two share their behaviour: class files whose name contains {@code $} are skipped,
 * because a Groovy closure compiles to a class that is never a component candidate, and any
 * {@link TypeFilter} the plugin manager contributes is applied as an include filter.</p>
 *
 * @since 8.0
 */
public class GrailsComponentScanPostProcessor implements BeanDefinitionRegistryPostProcessor {

    private final List<String> packagesToScan;
    private final GrailsPluginManager pluginManager;

    public GrailsComponentScanPostProcessor(List<String> packagesToScan, GrailsPluginManager pluginManager) {
        this.packagesToScan = packagesToScan;
        this.pluginManager = pluginManager;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (packagesToScan == null || packagesToScan.isEmpty()) {
            return;
        }

        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry);
        scanner.setResourceLoader(resourcePatternResolver());
        if (pluginManager != null) {
            for (TypeFilter typeFilter : pluginManager.getTypeFilters()) {
                scanner.addIncludeFilter(typeFilter);
            }
        }
        scanner.scan(packagesToScan.toArray(new String[0]));
    }

    private static PathMatchingResourcePatternResolver resourcePatternResolver() {
        PathMatchingResourcePatternResolver resolver =
                new PathMatchingResourcePatternResolver(new DefaultResourceLoader());
        resolver.setPathMatcher(new AntPathMatcher() {
            @Override
            public boolean match(String pattern, String path) {
                if (path.endsWith(".class") && GrailsStringUtils.getFileBasename(path).contains("$")) {
                    return false;
                }
                return super.match(pattern, path);
            }
        });
        return resolver;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // bean definitions are contributed in postProcessBeanDefinitionRegistry
    }

}
