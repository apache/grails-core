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

import org.springframework.beans.factory.BeanRegistrar

/**
 * API which plugins implement to provide behavior in defined application lifecycle hooks.
 *
 * The {@link GrailsApplicationLifeCycle#beanRegistrar()} method can be used to register Spring beans.
 *
 * @since 3.0
 * @see {@link grails.plugins.Plugin}
 */
interface GrailsApplicationLifeCycle {

    /**
     * Sub classes should override to provide implementations
     *
     * @return A closure that defines beans to be registered by Spring
     * @deprecated since 8.0 in favour of {@link #beanRegistrar()}. The underlying bean builder DSL
     * remains available but is no longer actively supported and will not receive fixes for new
     * issues — you are strongly urged to migrate to {@link #beanRegistrar()}.
     */
    @Deprecated(since = '8.0')
    Closure doWithSpring()

    /**
     * Sub classes should override to register beans with the Spring Framework
     * {@link org.springframework.beans.factory.BeanRegistry} using a {@link BeanRegistrar}.
     * This is the modern, Spring-native replacement for the {@link #doWithSpring()} bean builder DSL.
     *
     * <p>The returned registrar is applied before Spring Boot auto-configuration is processed, so
     * beans registered here take precedence over Boot's {@code @ConditionalOnMissingBean} defaults.</p>
     *
     * @return A {@link BeanRegistrar} that registers beans, or {@code null} if none (the default)
     * @since 8.0
     */
    default BeanRegistrar beanRegistrar() {
        return null
    }

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
