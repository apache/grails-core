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

/**
 * Without the auto-injected {@code @EnableWebMvc}, Spring Boot's
 * {@link WebMvcAutoConfiguration} is active and contributes a {@code defaultViewResolver}
 * ({@code InternalResourceViewResolver}). For a Grails application that does not use
 * JSP/InternalResource views (e.g. a REST/JSON-views app) this catch-all resolver resolves
 * any unmatched view name to a servlet forward, producing
 * {@code Circular view path [index]: would dispatch back to the current handler URL} errors.
 *
 * This is the single place the {@code defaultViewResolver} is removed for every Grails servlet
 * web application (GSP, JSON/Markup or plain), so Grails' own view resolution is used instead.
 * grails-gsp only loads for GSP applications, so the removal cannot live there. Ordered after
 * {@link WebMvcAutoConfiguration} so the bean exists by the time the registrar runs.
 *
 * <p>Disable with {@code grails.web.removeDefaultViewResolverBean=false}.
 */
@AutoConfiguration(after = WebMvcAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import(GrailsViewResolverAutoConfiguration.RemoveDefaultViewResolverRegistrar.class)
public class GrailsViewResolverAutoConfiguration {

    static class RemoveDefaultViewResolverRegistrar implements ImportBeanDefinitionRegistrar, EnvironmentAware {

        private boolean removeDefaultViewResolverBean = true;

        @Override
        public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
            if (removeDefaultViewResolverBean && registry.containsBeanDefinition("defaultViewResolver")) {
                registry.removeBeanDefinition("defaultViewResolver");
            }
        }

        @Override
        public void setEnvironment(Environment environment) {
            this.removeDefaultViewResolverBean = environment.getProperty(
                    "grails.web.removeDefaultViewResolverBean", Boolean.class, true);
        }
    }
}
