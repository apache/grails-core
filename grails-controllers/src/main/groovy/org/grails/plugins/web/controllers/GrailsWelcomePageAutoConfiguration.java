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
package org.grails.plugins.web.controllers;

import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

import grails.config.Settings;

/**
 * Without the auto-injected {@code @EnableWebMvc}, Spring Boot's
 * {@link WebMvcAutoConfiguration} is active and contributes a {@code welcomePageHandlerMapping}
 * (and a {@code welcomePageNotAcceptableHandlerMapping}) that maps the root path ('/') to a static
 * {@code index.html} found under {@code classpath:/META-INF/resources/}, {@code classpath:/resources/},
 * {@code classpath:/static/} or {@code classpath:/public/}. In a Grails application the root path is
 * expressed through {@code UrlMappings}, so this Boot convention overlaps with — and can shadow — the
 * application's own '/' mapping whenever such an {@code index.html} happens to exist.
 *
 * <p>This removes both welcome-page handler mappings for every Grails servlet web application so that
 * Grails' {@code UrlMappings} own the root path. Ordered after {@link WebMvcAutoConfiguration} so the
 * bean definitions exist by the time the registrar runs.
 *
 * <p>Disable with {@code grails.web.removeWelcomePageMapping=false} to restore Boot's welcome-page
 * behavior.
 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GrailsWelcomePageAutoConfiguration.RemoveWelcomePageMappingRegistrar.class)
public class GrailsWelcomePageAutoConfiguration {

    static final String REMOVE_PROPERTY = Settings.WEB_REMOVE_WELCOME_PAGE_MAPPING;

    static class RemoveWelcomePageMappingRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private static final String[] WELCOME_PAGE_BEANS = {
            "welcomePageHandlerMapping", "welcomePageNotAcceptableHandlerMapping"
        };

        private boolean removeWelcomePageMapping = true;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (!removeWelcomePageMapping) {
                return;
            }
            for (String beanName : WELCOME_PAGE_BEANS) {
                if (registry.containsBeanDefinition(beanName)) {
                    registry.removeBeanDefinition(beanName);
                }
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.removeWelcomePageMapping = environment.getProperty(REMOVE_PROPERTY, Boolean.class, true);
        }
    }
}
