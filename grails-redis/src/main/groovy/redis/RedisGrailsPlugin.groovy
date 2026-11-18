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

package redis

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.plugins.Plugin
import grails.plugins.redis.RedisBeanDefinitionsPostProcessor

class RedisGrailsPlugin extends Plugin {

    def grailsVersion = '8.0.0-SNAPSHOT > *'
    def pluginExcludes = [
            'codenarc.properties',
            'grails-app/conf/**',
            'grails-app/views/**',
            'grails-app/domain/**',
            'grails-app/services/test/**'
    ]

    def title = 'Redis Plugin' // Headline display name of the plugin
    def author = 'Apache Grails Team'
    def authorEmail = ''

    def description = '''The Redis plugin provides integration with a Redis datastore. Redis is a lightning fast 'data structure server'.  The plugin enables a number of memoization techniques to cache results from complex operations in Redis.'''
    def issueManagement = [system: 'github', url: 'https://github.com/grails-plugins/grails-redis/issues']
    def scm = [ url: 'https://github.com/apache/grails-core']
    def documentation = 'https://grails.apache.org/documentation.html'
    def license = 'APACHE'

    @Override
    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            // Read through the Grails config, not the Environment: map-shaped subtrees are
            // not resolvable as Environment properties
            Map redisConfigMap = (Map) (grailsApplication.config.get('grails.redis') ?: [:])

            // The redis pool definitions need destroy methods and dynamic pool-config property
            // values, which the BeanRegistry API cannot express — the definitions are contributed
            // by a dedicated post-processor instead
            registry.registerBean('redisBeanDefinitionsPostProcessor', RedisBeanDefinitionsPostProcessor) {
                it.infrastructure().supplier {
                    new RedisBeanDefinitionsPostProcessor(redisConfigMap)
                }
            }
        } as BeanRegistrar
    }
}
