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

package grails.plugin.springsecurity.cas.test

import groovy.transform.CompileStatic
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources

/**
 * Points the CAS plugin at the containerised CAS server before the application context is built.
 *
 * <p>The plugin reads {@code cas.serverUrlPrefix} while it is defining beans, so the container has
 * to be running by then. The two URLs CAS calls back on depend on the port the embedded server ends
 * up binding, which is not known this early; they are given a placeholder here and corrected by
 * {@link CasServiceUrlConfigurer} once the server has started.</p>
 */
@CompileStatic
class CasTestEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = 'casTestContainer'

    private static final String PREFIX = 'grails.plugin.springsecurity.cas.'
    private static final int PLACEHOLDER_PORT = 0

    @Override
    void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        MutablePropertySources propertySources = environment.propertySources
        if (propertySources.contains(PROPERTY_SOURCE_NAME)) {
            return
        }

        Map<String, Object> properties = [:]
        properties.put("${PREFIX}serverUrlPrefix".toString(), CasContainerHolder.serverUrlPrefix)
        properties.put("${PREFIX}serviceUrl".toString(), CasTestConfig.serviceUrl(PLACEHOLDER_PORT))
        if (CasTestConfig.proxyEnabled) {
            properties.put("${PREFIX}proxyReceptorUrl".toString(), CasTestConfig.PROXY_RECEPTOR_URL)
            properties.put("${PREFIX}proxyCallbackUrl".toString(), CasTestConfig.proxyCallbackUrl(PLACEHOLDER_PORT))
        }

        propertySources.addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties))
    }
}
