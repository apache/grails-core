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
package grails.core

/**
 * API which plugins implement to provide behavior in defined application lifecycle hooks.
 *
 * The {@link GrailsApplicationLifeCycle#doWithSpring()} method can be used register Spring beans.
 *
 * @since 3.0
 * @see {@link grails.plugins.Plugin}
 */
interface GrailsApplicationLifeCycle {

    /**
     * Sub classes should override to provide implementations
     *
     * @return A closure that defines beans to be registered by Spring
     */
    Closure doWithSpring()

    /**
     * Sub classes should override to register Spring beans <em>before</em> Spring Boot
     * auto-configuration runs. Beans registered here are placed in the bean registry ahead of
     * auto-configuration, so Boot beans guarded by {@code @ConditionalOnMissingBean} back off in
     * favour of the plugin's bean — instead of the plugin having to override or remove the Boot
     * bean afterwards (the legacy behaviour of {@link #doWithSpring()}).
     *
     * <p>This is the modern, Boot-aligned registration phase. Use it for beans that need to win
     * over a Spring Boot auto-configuration default. Beans that iterate Grails artefacts
     * (controllers, services, taglibs) must stay in {@link #doWithSpring()}, which runs after
     * artefact discovery.
     *
     * <p><strong>Contract:</strong> the returned closure must <em>only define bean definitions</em>
     * and must be free of side effects. This phase runs before the main Grails lifecycle, in an
     * isolated lightweight pass that instantiates plugins purely to collect their bean definitions;
     * those plugin instances are then discarded and the real lifecycle re-instantiates them. A
     * closure with side effects (mutating shared/static state, doing I/O, registering listeners)
     * would therefore run twice. Read configuration and reference other beans freely — just do not
     * <em>do</em> anything beyond declaring beans. Reading {@code grailsApplication.config} is safe;
     * iterating {@code grailsApplication} artefacts is not (they are not discovered yet — use
     * {@link #doWithSpring()} for those).
     *
     * <p><strong>Plugins only.</strong> This phase is drained for plugins. An <em>application</em>
     * class does not need it: it is a {@code @Configuration}, and Spring Boot processes user
     * configuration before auto-configuration, so a plain {@code @Bean} in the application class
     * already registers ahead of auto-config and Boot's {@code @ConditionalOnMissingBean} beans
     * defer to it. (Draining the application class here is also unsafe — its
     * {@code grailsApplication} is not yet available this early.) So override this on a
     * {@link grails.plugins.Plugin}; in an application class, declare a {@code @Bean} instead.
     *
     * @return A closure that defines beans to be registered before auto-configuration
     * @since 8.0
     */
    default Closure doWithSpringBeforeAutoConfiguration() { null }

    /**
     * Invoked once the {@link org.springframework.context.ApplicationContext} has been refreshed in a phase where plugins can add dynamic methods. Subclasses should override
     */
    void doWithDynamicMethods()
    /**
     * Invoked once the {@link org.springframework.context.ApplicationContext} has been refreshed and after {#doWithDynamicMethods()} is invoked. Subclasses should override
     */
    void doWithApplicationContext()

    /**
     * Invoked when the application configuration changes
     *
     * @param event The event
     */
    void onConfigChange(Map<String, Object> event)

    /**
     * Invoked once all prior initialization hooks: {@link GrailsApplicationLifeCycle#doWithSpring()}, {@link GrailsApplicationLifeCycle#doWithDynamicMethods()} and {@link GrailsApplicationLifeCycle#doWithApplicationContext()}
     *
     * @param event The event
     */
    void onStartup(Map<String, Object> event)
    /**
     * Invoked when the {@link org.springframework.context.ApplicationContext} is closed
     *
     * @param event The event
     */
    void onShutdown(Map<String, Object> event)
}
