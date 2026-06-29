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
            def host = redisConfigMap?.host ?: 'localhost'
            def port = redisConfigMap.containsKey("port") ? "${redisConfigMap.port}" as Integer : Protocol.DEFAULT_PORT
            def timeout = redisConfigMap.containsKey("timeout") ? "${redisConfigMap?.timeout}" as Integer : Protocol.DEFAULT_TIMEOUT
            def password = redisConfigMap?.password ?: null
            def database = redisConfigMap?.database ?: Protocol.DEFAULT_DATABASE
            def sentinels = redisConfigMap?.sentinels ?: null
            def masterName = redisConfigMap?.masterName ?: null
            def useSSL = redisConfigMap?.useSSL ?: false

            // If sentinels and a masterName is present, using different pool implementation
            if (sentinels && masterName) {
                if (sentinels instanceof String) {
                    sentinels = Eval.me(sentinels.toString())
                }

                if (sentinels instanceof Collection) {
                    "redisPool${key}"(JedisSentinelPool, masterName, sentinels as Set, ref(poolBean), timeout, password, database, useSSL) { bean ->
                        bean.destroyMethod = 'destroy'
                    }
                } else {
                    throw new RuntimeException('Redis configuraiton property [sentinels] does not appear to be a valid collection.')
                }
            } else {
                "redisPool${key}"(JedisPool, ref(poolBean), host, port, timeout, password, database, useSSL) { bean ->
                    bean.destroyMethod = 'destroy'
                }
            }

            "redisService${key}"(serviceClass) {
                redisPool = ref("redisPool${key}")
            }
        }
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
