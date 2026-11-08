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

package org.grails.plugins.domain

import groovy.transform.CompileStatic

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

import grails.util.Environment as GrailsEnvironment
import org.grails.datastore.mapping.config.Settings as DatastoreSettings

/**
 * Defaults auto-timestamp annotation caching to off in development mode, so that
 * reloading a domain class picks up changed annotations.
 *
 * <p>The default has to reach the {@link ConfigurableEnvironment}: both consumers —
 * {@code AutoTimestampEventListener} and {@code DomainModelServiceImpl} — read it
 * through a {@code @Value("${...}")} placeholder, and placeholders resolve against
 * the environment. Writing it into the Grails config with {@code Config.put} reached
 * neither, since that mutates only the config's own map and
 * {@code GrailsPlaceholderConfigurer} never receives the configuration to fold in.</p>
 */
@CompileStatic
class DomainClassEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PROPERTY_SOURCE_NAME = 'defaultDomainClassProperties'

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getProperty(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS) != null) {
            return
        }
        Map<String, Object> defaults = [:]
        defaults.put(DatastoreSettings.SETTING_AUTO_TIMESTAMP_CACHE_ANNOTATIONS, !GrailsEnvironment.isDevelopmentMode())
        environment.propertySources.addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, defaults))
    }

    /**
     * Ordered last so the configuration data post-processor has already loaded the
     * application's own configuration, making the check above meaningful. The source
     * is appended with lowest precedence regardless, so application configuration
     * wins either way.
     */
    @Override
    int getOrder() {
        Ordered.LOWEST_PRECEDENCE
    }

}
