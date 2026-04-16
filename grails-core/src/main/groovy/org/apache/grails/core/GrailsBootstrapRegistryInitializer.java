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
package org.apache.grails.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.bootstrap.BootstrapRegistry;
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer;

import org.apache.grails.core.plugins.DefaultPluginDiscovery;
import org.apache.grails.core.plugins.PluginDiscovery;

/**
 * Registers the {@link PluginDiscovery} in the Spring Boot Bootstrap context so it can be accessed during
 * the early lifecycle of the application & later promoted to actual bean.
 *
 * <p>This ensures that both the early-lifecycle
 * {@link grails.boot.config.GrailsEnvironmentPostProcessor} and the
 * later-lifecycle {@link grails.plugins.DefaultGrailsPluginManager} can
 * access the same discovered, filtered, and sorted set of plugins.</p>
 *
 * <p>This class is registered via {@code META-INF/spring.factories} under the
 * {@code org.springframework.boot.bootstrap.BootstrapRegistryInitializer} key.</p>
 *
 * @since 7.1
 */
public class GrailsBootstrapRegistryInitializer implements BootstrapRegistryInitializer {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsBootstrapRegistryInitializer.class);

    @Override
    public void initialize(BootstrapRegistry registry) {
        LOG.debug("Registering GrailsPluginDiscovery in BootstrapRegistry");
        registry.register(PluginDiscovery.class, context -> new DefaultPluginDiscovery());

        // Promote the GrailsPluginDiscovery singleton to the ApplicationContext
        // so that later-lifecycle components (e.g., DefaultGrailsPluginManager)
        // can access it. This fires after the context is prepared but before
        // refresh(), so the bean is available during the full Spring lifecycle.
        registry.addCloseListener(event -> {
            PluginDiscovery discovery = event.getBootstrapContext()
                    .get(PluginDiscovery.class);
            event.getApplicationContext()
                    .getBeanFactory()
                    .registerSingleton(PluginDiscovery.BEAN_NAME, discovery);
            LOG.debug("Promoted GrailsPluginDiscovery to ApplicationContext as '{}'", PluginDiscovery.BEAN_NAME);
        });
    }
}
