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

import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.DefaultListableBeanFactory
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.core.Ordered

import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisSentinelPool

import spock.lang.Specification

class RedisBeanDefinitionsPostProcessorSpec extends Specification {

    void "registers the pool and service definitions for the default and named connections"() {
        given:
        def registry = new DefaultListableBeanFactory()
        def postProcessor = new RedisBeanDefinitionsPostProcessor([
                poolConfig: [maxTotal: 10],
                connections: [cache: [host: 'redis.example.com', port: 6380]]])

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then: 'the default connection beans are registered'
        registry.containsBeanDefinition('redisPoolConfig')
        registry.getBeanDefinition('redisPool').beanClassName == JedisPool.name
        registry.getBeanDefinition('redisService').beanClassName == RedisService.name

        and: 'the pool destroys with the context'
        ((AbstractBeanDefinition) registry.getBeanDefinition('redisPool')).destroyMethodName == 'destroy'

        and: 'valid pool config properties are applied to the pool config definition'
        registry.getBeanDefinition('redisPoolConfig').propertyValues.getPropertyValue('maxTotal').value == 10

        and: 'the named connection beans are registered with the capitalized suffix'
        registry.containsBeanDefinition('redisPoolConfigCache')
        registry.containsBeanDefinition('redisPoolCache')
        registry.containsBeanDefinition('redisServiceCache')

        and:
        postProcessor.order == Ordered.HIGHEST_PRECEDENCE
    }

    void "a sentinel configuration registers a sentinel pool"() {
        given:
        def registry = new DefaultListableBeanFactory()
        def postProcessor = new RedisBeanDefinitionsPostProcessor([
                masterName: 'mymaster',
                sentinels: ['sentinel1:26379', 'sentinel2:26379']])

        when:
        postProcessor.postProcessBeanDefinitionRegistry(registry)

        then:
        registry.getBeanDefinition('redisPool').beanClassName == JedisSentinelPool.name
    }

    void "the service definition wires the redis pool by reference"() {
        given:
        def registry = new DefaultListableBeanFactory()

        when:
        new RedisBeanDefinitionsPostProcessor([:]).postProcessBeanDefinitionRegistry(registry)
        def serviceDefinition = registry.getBeanDefinition('redisService')

        then:
        serviceDefinition.propertyValues.getPropertyValue('redisPool').value.beanName == 'redisPool'
    }

    void "an existing redis bean definition wins"() {
        given:
        def registry = new DefaultListableBeanFactory()
        registry.registerBeanDefinition('redisPool', new GenericBeanDefinition(beanClass: String))
        registry.registerBeanDefinition('redisServiceCache', new GenericBeanDefinition(beanClass: String))

        when:
        new RedisBeanDefinitionsPostProcessor([connections: [cache: [:]]]).postProcessBeanDefinitionRegistry(registry)

        then: 'the pre-existing definitions are not overwritten'
        registry.getBeanDefinition('redisPool').beanClassName == String.name
        registry.getBeanDefinition('redisServiceCache').beanClassName == String.name

        and: 'the remaining redis beans are still contributed'
        registry.getBeanDefinition('redisService').beanClassName == RedisService.name
        registry.containsBeanDefinition('redisPoolConfig')
        registry.getBeanDefinition('redisPoolCache').beanClassName == JedisPool.name
    }
}
