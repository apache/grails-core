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

package grails.plugins.redis.util

import groovy.util.logging.Slf4j

import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.BeanDefinitionRegistry
import org.springframework.beans.factory.support.GenericBeanDefinition

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisSentinelPool
import redis.clients.jedis.Protocol

/**
 * This class provides a closure that can (and must) be used within the context of a BeanBuilder.
 * To wire all redisServices using a custom class do the following
 *
 * def configureService = RedisConfigurationUtil.configureService
 * def redisConfigMap = application.config.grails.redis ?: [:]
 *
 * configureService.delegate = delegate
 * configureService(redisConfigMap, "", MyRedisService)
 * redisConfigMap?.connections?.each { connection ->
 *   configureService(connection.value, connection?.key?.capitalize(), MyRedisService)
 *}*
 */
@Slf4j
class RedisConfigurationUtil {

    /**
     * delegate to wire up the required beans.
     */
    static def configureService = { delegate, redisConfigMap, key, serviceClass ->

        def poolBean = "redisPoolConfig${key}"
        def validPoolProperties = findValidPoolProperties(redisConfigMap.poolConfig)

        //todo: fix the validPoolProperty eval or just add them inline
        delegate."${poolBean}"(JedisPoolConfig) {
            validPoolProperties.each { configKey, value ->
                delegate.setProperty(configKey, value)
            }
        }
//        delegate."${poolBean}"(JedisPoolConfig) { bean ->
//            validPoolProperties.each { configKey, value ->
//                bean.setProperty(configKey, value)
////                bean[configKey] = value
//                if(bean.class.)
//                bean."${configKey}" = value
//            }
//        }

        delegate.with {
            Map settings = RedisConfigurationUtil.parseConnectionSettings(redisConfigMap)

            // If sentinels and a masterName is present, using different pool implementation
            if (settings.sentinels && settings.masterName) {
                Collection sentinels = RedisConfigurationUtil.resolveSentinels(settings.sentinels)
                "redisPool${key}"(JedisSentinelPool, settings.masterName, sentinels as Set, ref(poolBean),
                        settings.timeout, settings.password, settings.database, settings.useSSL) { bean ->
                    bean.destroyMethod = 'destroy'
                }
            } else {
                "redisPool${key}"(JedisPool, ref(poolBean), settings.host, settings.port,
                        settings.timeout, settings.password, settings.database, settings.useSSL) { bean ->
                    bean.destroyMethod = 'destroy'
                }
            }

            "redisService${key}"(serviceClass) {
                redisPool = ref("redisPool${key}")
            }
        }
    }

    /**
     * Registers the pool-config, pool and service bean definitions for a redis connection
     * directly against a {@link BeanDefinitionRegistry}, mirroring the beans the
     * {@link #configureService} closure wires through the bean builder DSL. Used by the redis
     * plugin's {@code beanRegistrar()}-registered post-processor. An existing definition for any
     * of the bean names wins, preserving the ability of the application (or another plugin) to
     * override the beans.
     */
    static void configureService(BeanDefinitionRegistry registry, def redisConfigMap, String key, Class serviceClass) {
        String poolConfigBeanName = "redisPoolConfig${key}"
        if (!registry.containsBeanDefinition(poolConfigBeanName)) {
            def validPoolProperties = findValidPoolProperties(redisConfigMap?.poolConfig)
            GenericBeanDefinition poolConfigDefinition = new GenericBeanDefinition(beanClass: JedisPoolConfig)
            validPoolProperties?.each { configKey, value ->
                poolConfigDefinition.propertyValues.addPropertyValue(configKey.toString(), value)
            }
            registry.registerBeanDefinition(poolConfigBeanName, poolConfigDefinition)
        }

        String poolBeanName = "redisPool${key}"
        if (!registry.containsBeanDefinition(poolBeanName)) {
            Map settings = parseConnectionSettings(redisConfigMap)

            GenericBeanDefinition poolDefinition = new GenericBeanDefinition(destroyMethodName: 'destroy')
            // If sentinels and a masterName is present, using different pool implementation
            if (settings.sentinels && settings.masterName) {
                Collection sentinels = resolveSentinels(settings.sentinels)
                poolDefinition.beanClass = JedisSentinelPool
                poolDefinition.constructorArgumentValues.with {
                    addIndexedArgumentValue(0, settings.masterName)
                    addIndexedArgumentValue(1, sentinels as Set)
                    addIndexedArgumentValue(2, new RuntimeBeanReference(poolConfigBeanName))
                    addIndexedArgumentValue(3, settings.timeout)
                    addIndexedArgumentValue(4, settings.password)
                    addIndexedArgumentValue(5, settings.database)
                    addIndexedArgumentValue(6, settings.useSSL)
                }
            } else {
                poolDefinition.beanClass = JedisPool
                poolDefinition.constructorArgumentValues.with {
                    addIndexedArgumentValue(0, new RuntimeBeanReference(poolConfigBeanName))
                    addIndexedArgumentValue(1, settings.host)
                    addIndexedArgumentValue(2, settings.port)
                    addIndexedArgumentValue(3, settings.timeout)
                    addIndexedArgumentValue(4, settings.password)
                    addIndexedArgumentValue(5, settings.database)
                    addIndexedArgumentValue(6, settings.useSSL)
                }
            }
            registry.registerBeanDefinition(poolBeanName, poolDefinition)
        }

        String serviceBeanName = "redisService${key}"
        if (!registry.containsBeanDefinition(serviceBeanName)) {
            GenericBeanDefinition serviceDefinition = new GenericBeanDefinition(beanClass: serviceClass)
            serviceDefinition.propertyValues.addPropertyValue('redisPool', new RuntimeBeanReference(poolBeanName))
            registry.registerBeanDefinition(serviceBeanName, serviceDefinition)
        }
    }

    /**
     * Parses the connection settings shared by both {@code configureService} variants from a
     * redis connection config map, applying the plugin's defaults.
     */
    private static Map parseConnectionSettings(def redisConfigMap) {
        [host: redisConfigMap?.host ?: 'localhost',
         port: redisConfigMap.containsKey('port') ? "${redisConfigMap.port}" as Integer : Protocol.DEFAULT_PORT,
         timeout: redisConfigMap.containsKey('timeout') ? "${redisConfigMap?.timeout}" as Integer : Protocol.DEFAULT_TIMEOUT,
         password: redisConfigMap?.password ?: null,
         database: redisConfigMap?.database ?: Protocol.DEFAULT_DATABASE,
         sentinels: redisConfigMap?.sentinels ?: null,
         masterName: redisConfigMap?.masterName ?: null,
         useSSL: redisConfigMap?.useSSL ?: false]
    }

    /**
     * Normalizes the {@code sentinels} setting to a collection, evaluating a String value the way
     * the original bean-builder wiring did.
     */
    private static Collection resolveSentinels(def sentinels) {
        def resolved = sentinels instanceof String ? Eval.me(sentinels.toString()) : sentinels
        if (!(resolved instanceof Collection)) {
            throw new RuntimeException('Redis configuraiton property [sentinels] does not appear to be a valid collection.')
        }
        (Collection) resolved
    }

    static def findValidPoolProperties(def properties) {
        def fakeJedisPoolConfig = new JedisPoolConfig()
        properties?.findAll { configKey, value ->
            try {
                fakeJedisPoolConfig[configKey] = value
                return true
            } catch (Exception ignore) {
                log.warn('Redis pool configuration parameter ({}) does not exist on JedisPoolConfig or value is the wrong type', configKey.toString())
                return false
            }
        }
    }
}
