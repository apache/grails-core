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

import java.util.Collections;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Defaults {@code spring.web.resources.add-mappings} to {@code false} for Grails servlet web
 * applications. Grails 8 no longer injects {@code @EnableWebMvc}, so Spring Boot's
 * {@code WebMvcAutoConfiguration} is active and would otherwise register a catch-all ('/**')
 * static-resource handler over {@code classpath:/META-INF/resources/}, {@code classpath:/resources/},
 * {@code classpath:/static/} and {@code classpath:/public/}. Grails already serves those same
 * locations under {@code /static/**} (see {@code ControllersAutoConfiguration}), so the Boot
 * catch-all is redundant and would change the 404 semantics of unmapped URLs by letting files in
 * those locations shadow them.
 *
 * <p>Contributed as a Spring Boot default property (lowest precedence), so an application can restore
 * Boot's behavior with {@code spring.web.resources.add-mappings=true}.
 */
public class GrailsWebResourcesEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String ADD_MAPPINGS_PROPERTY = "spring.web.resources.add-mappings";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        DefaultPropertiesPropertySource.addOrMerge(
                Collections.<String, Object>singletonMap(ADD_MAPPINGS_PROPERTY, "false"),
                environment.getPropertySources());
    }
}
