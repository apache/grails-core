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
package grails.plugin.springsecurity

import groovy.transform.CompileStatic

import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter
import org.springframework.boot.autoconfigure.AutoConfigurationMetadata
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment

/**
 * Automatically excludes Spring Boot security auto-configuration classes that
 * conflict with the Grails Spring Security plugin.
 *
 * <p>When the Grails Spring Security plugin is on the classpath, Spring Boot's
 * security auto-configurations (e.g. {@code SecurityAutoConfiguration},
 * {@code SecurityFilterAutoConfiguration}) create duplicate
 * {@code SecurityFilterChain} beans and other security infrastructure that
 * conflicts with the plugin's own bean definitions in
 * {@link SpringSecurityCoreGrailsPlugin#doWithSpring}.</p>
 *
 * <p>Previously, users had to manually exclude up to 7 auto-configuration classes
 * in {@code application.yml}. This filter removes that requirement by
 * automatically filtering them out during Spring Boot's auto-configuration
 * discovery phase.</p>
 *
 * <p>To disable this filter and allow Spring Boot's security auto-configurations
 * to run, set the following property in {@code application.yml}:</p>
 *
 * <pre>
 * grails:
 *   plugin:
 *     springsecurity:
 *       excludeSpringSecurityAutoConfiguration: false
 * </pre>
 *
 * <p>Registered via {@code META-INF/spring.factories} as an
 * {@link AutoConfigurationImportFilter}. This runs before auto-configuration
 * bytecode is loaded, so there is no performance overhead from excluded classes.</p>
 *
 * @since 7.0.2
 * @see AutoConfigurationImportFilter
 */
@CompileStatic
class SecurityAutoConfigurationExcluder implements AutoConfigurationImportFilter, EnvironmentAware {

    static final String ENABLED_PROPERTY = 'grails.plugin.springsecurity.excludeSpringSecurityAutoConfiguration'

    private boolean enabled = true

    @Override
    void setEnvironment(Environment environment) {
        this.enabled = environment.getProperty(ENABLED_PROPERTY, Boolean, true)
    }

    /**
     * Spring Boot security auto-configuration classes that conflict with the
     * Grails Spring Security plugin. These are excluded unconditionally when the
     * plugin is on the classpath.
     *
     * <ul>
     *   <li>{@code SecurityAutoConfiguration} — creates a default {@code SecurityFilterChain}
     *       that conflicts with the plugin's {@code FilterChainProxy}</li>
     *   <li>{@code SecurityFilterAutoConfiguration} — registers a
     *       {@code DelegatingFilterProxyRegistrationBean} that duplicates the plugin's
     *       {@code springSecurityFilterChainRegistrationBean}</li>
     *   <li>{@code UserDetailsServiceAutoConfiguration} — creates an in-memory
     *       {@code UserDetailsService} that conflicts with the plugin's
     *       {@code GormUserDetailsService}</li>
     *   <li>{@code OAuth2ClientAutoConfiguration} ({@code ...oauth2.client.servlet}) —
     *       conflicts when the plugin-oauth2 module manages OAuth2 configuration</li>
     *   <li>{@code OAuth2ClientAutoConfiguration} ({@code ...oauth2.client}) —
     *       non-servlet variant of the above; also conflicts with plugin-oauth2</li>
     *   <li>{@code OAuth2ResourceServerAutoConfiguration} — conflicts with the
     *       plugin's resource server security setup</li>
     *   <li>{@code ManagementWebSecurityAutoConfiguration} — Actuator security
     *       that conflicts when Actuator is on the classpath</li>
     * </ul>
     */
    private static final Set<String> EXCLUDED_AUTO_CONFIGURATIONS = [
            'org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration',
            'org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration',
            'org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration',
            'org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration',
            'org.springframework.boot.autoconfigure.security.oauth2.client.OAuth2ClientAutoConfiguration',
            'org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration',
            'org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration',
    ].toSet().asImmutable()

    @Override
    boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
        autoConfigurationClasses.collect {
            !enabled || !(it in EXCLUDED_AUTO_CONFIGURATIONS)
        } as boolean[]
    }

    /**
     * Returns the set of auto-configuration class names that this filter excludes.
     * Exposed for testing and diagnostic purposes.
     *
     * @return unmodifiable set of excluded class names
     */
    static Set<String> getExcludedAutoConfigurations() {
        EXCLUDED_AUTO_CONFIGURATIONS
    }
}
