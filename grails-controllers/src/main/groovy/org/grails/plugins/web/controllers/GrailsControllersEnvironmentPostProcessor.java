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

import org.jspecify.annotations.NonNull;

import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Environment post processor that checks for legacy multipart configuration properties.
 *
 * @since 8.0
 */
public class GrailsControllersEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String LEGACY_MULTIPART_CONFIGURATION = "grails.controllers.upload";

    private static final String LEGACY_MULTIPART_CONFIGURATION_ERROR =
            "Configuration properties under 'grails.controllers.upload' are no longer supported. " +
            "Use Spring Boot's 'spring.servlet.multipart' configuration instead. For example, set " +
            "'spring.servlet.multipart.maxFileSize=200MB' and " +
            "'spring.servlet.multipart.maxRequestSize=200MB'.";

    @Override
    public void postProcessEnvironment(@NonNull ConfigurableEnvironment environment, @NonNull SpringApplication application) {
        var legacyMultipartConfig = Binder.get(environment)
                .bind(LEGACY_MULTIPART_CONFIGURATION, Bindable.mapOf(String.class, Object.class));
        if (legacyMultipartConfig.isBound()) {
            throw new IllegalStateException(
                LEGACY_MULTIPART_CONFIGURATION_ERROR +
                    " Found: " + legacyMultipartConfig.get().keySet());
        }
    }
}
