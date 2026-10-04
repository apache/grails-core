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
package org.grails.datastore.gorm.neo4j.config

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import org.neo4j.driver.Config

import org.springframework.core.env.PropertyResolver
import org.springframework.util.ReflectionUtils

import org.grails.datastore.mapping.config.ConfigurationBuilder

/**
 * Constructs the Neo4j driver configuration
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
@Slf4j
class Neo4jDriverConfigBuilder extends ConfigurationBuilder<Config.ConfigBuilder, Config> {

    private static final Config DRIVER_DEFAULTS = Config.defaultConfig()

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver) {
        super(propertyResolver, Settings.SETTING_NEO4J_DRIVER_PROPERTIES, 'with')
    }

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver, String configurationPrefix) {
        super(propertyResolver, configurationPrefix, 'with')
    }

    Neo4jDriverConfigBuilder(PropertyResolver propertyResolver, String configurationPrefix, Object fallBackConfiguration) {
        super(propertyResolver, configurationPrefix, fallBackConfiguration, 'with')
    }

    @Override
    protected Config.ConfigBuilder createBuilder() {
        return Config.builder()
    }

    @Override
    protected Config toConfiguration(Config.ConfigBuilder builder) {
        return builder.build()
    }

    /**
     * A connection's options fall back to the default connection's built {@link Config}, but only where that one was
     * customized: a value still equal to the driver's own default is not passed on. Some of those defaults are
     * sentinels that the builder's setter rejects, such as {@code eventLoopThreads()}, which is {@code 0} until set,
     * so passing them on made every connection declared under {@code grails.neo4j.connections} fail to start.
     */
    @Override
    protected Object getFallBackValue(Object fallBackConfig, String methodName) {
        if (fallBackConfig != null) {
            Method fallBackMethod = ReflectionUtils.findMethod(fallBackConfig.getClass(), methodName)
            if (fallBackMethod != null && Modifier.isPublic(fallBackMethod.getModifiers())) {
                Object value = fallBackMethod.invoke(fallBackConfig)
                if (fallBackConfig instanceof Config && value == fallBackMethod.invoke(DRIVER_DEFAULTS)) {
                    return null
                }
                return value
            } else {
                return super.getFallBackValue(fallBackConfig, methodName)
            }
        }
        return null
    }
}
