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
package org.grails.plugins.sitemesh3;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Seeds default properties consumed by the upstream
 * {@code SiteMeshViewResolverAutoConfiguration}:
 *
 * <ul>
 *     <li>{@code sitemesh.integration=view-resolver} — activates the Spring
 *     MVC {@code ViewResolver} integration instead of the servlet-filter
 *     integration.</li>
 *     <li>{@code sitemesh.viewResolver.wrapMode=bean-instance} — selects
 *     the live-bean {@code SiteMeshViewResolverBeanPostProcessor}. The
 *     default {@code bean-definition} variant cannot find
 *     {@code gspViewResolver} because its bean definition is registered
 *     after {@code BeanDefinitionRegistryPostProcessors} fire.</li>
 *     <li>{@code sitemesh.viewResolver.targetBeanName=gspViewResolver} —
 *     tells the post-processor which view resolver to wrap (the default is
 *     {@code jspViewResolver}).</li>
 * </ul>
 *
 * <p>Each value is only set when absent so an application can opt-out by
 * explicitly setting any of these properties.</p>
 */
public class Sitemesh3EnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String PROPERTY_SOURCE_NAME = "grailsSitemesh3Defaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> props = new HashMap<>();
        if (environment.getProperty("sitemesh.integration") == null) {
            props.put("sitemesh.integration", "view-resolver");
        }
        if (environment.getProperty("sitemesh.viewResolver.wrapMode") == null) {
            props.put("sitemesh.viewResolver.wrapMode", "bean-instance");
        }
        if (environment.getProperty("sitemesh.viewResolver.targetBeanName") == null) {
            props.put("sitemesh.viewResolver.targetBeanName", "jspViewResolver");
        }
        if (!props.isEmpty()) {
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, props));
        }
    }
}
