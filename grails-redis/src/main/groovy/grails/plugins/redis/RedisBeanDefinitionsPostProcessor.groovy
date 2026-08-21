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
package grails.plugins.redis

import org.springframework.beans.BeansException
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor
import org.springframework.core.Ordered
import org.springframework.core.PriorityOrdered

import grails.plugins.redis.util.RedisConfigurationUtil

/**
 * Registers the redis pool and service bean definitions for the default connection and every
 * configured named connection, replacing the registration the redis plugin previously performed
 * through the {@code doWithSpring()} bean DSL. The pool beans need a {@code destroy} method and
 * per-connection pool-config property values, which the
 * {@link org.springframework.beans.factory.BeanRegistry} API cannot express, so the definitions
 * are contributed by this post-processor instead. An existing definition for a bean name wins,
 * preserving the ability of the application (or another plugin) to override the beans.
 *
 * <p>Runs as a {@link PriorityOrdered} post-processor with highest precedence so the redis
 * definitions are registered before Spring Boot's configuration-class post-processor evaluates
 * auto-configuration conditions — the same visibility the {@code doWithSpring()} registration had.</p>
 *
 * <p>Not statically compiled: the redis configuration is an untyped map traversed dynamically.</p>
 *
 * @since 8.0
 */
class RedisBeanDefinitionsPostProcessor implements BeanDefinitionRegistryPostProcessor, PriorityOrdered {

    private final Map redisConfigMap

    RedisBeanDefinitionsPostProcessor(Map redisConfigMap) {
        this.redisConfigMap = redisConfigMap ?: [:]
    }

    @Override
    void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        RedisConfigurationUtil.configureService(registry, redisConfigMap, '', RedisService)
        redisConfigMap.connections?.each { connection ->
            RedisConfigurationUtil.configureService(registry, connection.value, connection.key?.capitalize(), RedisService)
        }
    }

    @Override
    int getOrder() {
        Ordered.HIGHEST_PRECEDENCE
    }
}
