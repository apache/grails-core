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
package org.grails.plugins.i18n;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

/**
 * When an application declares {@code @EnableWebMvc}, Spring's {@link WebMvcConfigurationSupport}
 * contributes its own {@code localeResolver} bean (an {@code AcceptHeaderLocaleResolver}). That bean
 * exists before {@link I18nAutoConfiguration} is evaluated, so its
 * {@code @ConditionalOnMissingBean(name = "localeResolver")} backs off and Grails' configured
 * resolver (a {@code SessionLocaleResolver} by default) never registers — silently disabling
 * {@code ?lang=} switching. This was not the case before {@code WebMvcConfigurationSupport} gained a
 * {@code localeResolver} bean: Grails' resolver used to take precedence, matching Grails 7 behavior.
 *
 * <p>This removes the {@code WebMvcConfigurationSupport}-contributed {@code localeResolver} so that
 * {@link I18nAutoConfiguration} registers the Grails-configured resolver instead. A
 * {@code localeResolver} contributed by the application itself (for example via
 * {@code resources.groovy} or a user {@code @Configuration}) is left untouched, so an explicit
 * application resolver still wins. Ordered before {@link I18nAutoConfiguration} so the removal
 * happens before that configuration's condition is evaluated.
 */
@AutoConfiguration(before = I18nAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GrailsLocaleResolverAutoConfiguration.RemoveWebMvcSupportLocaleResolverRegistrar.class)
public class GrailsLocaleResolverAutoConfiguration {

    static class RemoveWebMvcSupportLocaleResolverRegistrar implements ImportBeanDefinitionRegistrar, BeanClassLoaderAware {

        private static final Logger LOG = LoggerFactory.getLogger(RemoveWebMvcSupportLocaleResolverRegistrar.class);

        private static final String LOCALE_RESOLVER_BEAN_NAME = DispatcherServlet.LOCALE_RESOLVER_BEAN_NAME;

        private ClassLoader classLoader;

        @Override
        public void setBeanClassLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
        }

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (!registry.containsBeanDefinition(LOCALE_RESOLVER_BEAN_NAME)) {
                return;
            }
            if (isWebMvcSupportContributed(registry.getBeanDefinition(LOCALE_RESOLVER_BEAN_NAME), registry)) {
                registry.removeBeanDefinition(LOCALE_RESOLVER_BEAN_NAME);
                LOG.debug("Removed the WebMvcConfigurationSupport '{}' bean so the Grails locale resolver takes precedence",
                        LOCALE_RESOLVER_BEAN_NAME);
            }
        }

        // True only when the bean is the localeResolver @Bean factory method of a
        // WebMvcConfigurationSupport (i.e. contributed by @EnableWebMvc), not an application bean.
        private boolean isWebMvcSupportContributed(BeanDefinition beanDefinition, BeanDefinitionRegistry registry) {
            String factoryBeanName = beanDefinition.getFactoryBeanName();
            if (factoryBeanName == null || !LOCALE_RESOLVER_BEAN_NAME.equals(beanDefinition.getFactoryMethodName()) ||
                    !registry.containsBeanDefinition(factoryBeanName)) {
                return false;
            }
            String factoryClassName = registry.getBeanDefinition(factoryBeanName).getBeanClassName();
            if (factoryClassName == null) {
                return false;
            }
            try {
                return WebMvcConfigurationSupport.class.isAssignableFrom(ClassUtils.forName(factoryClassName, classLoader));
            }
            catch (ClassNotFoundException ex) {
                return false;
            }
        }
    }
}
