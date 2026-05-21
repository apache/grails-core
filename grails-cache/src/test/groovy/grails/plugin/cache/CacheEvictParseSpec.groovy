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

package grails.plugin.cache

import org.grails.plugin.cache.GrailsCacheManager
import org.springframework.cache.Cache
import spock.lang.Specification

class CacheEvictParseSpec extends Specification {

    void "test simple usage"() {
        given:
        GrailsCacheManager cacheManager = Mock(GrailsCacheManager)
        GrailsCacheKeyGenerator keyGenerator = Mock(GrailsCacheKeyGenerator)
        def cache = Mock(Cache)
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {

    @CacheEvict('sum')
    def evict(String foo) {
        
    }
}
return TestService

''')
        when:
        def instance = testService.newInstance()
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__customCacheKeyGenerator" = keyGenerator
        instance.evict("a")

        then:
        1 * keyGenerator.generate("TestService", "evict", _, [foo:"a"]) >> "a"
        1 * cacheManager.getCache("sum") >> cache
        1 * cache.evict("a")
    }

    void "test evict with key closure"() {
        given:
        GrailsCacheManager cacheManager = Mock(GrailsCacheManager)
        GrailsCacheKeyGenerator keyGenerator = Mock(GrailsCacheKeyGenerator)
        def cache = Mock(Cache)
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {

    @CacheEvict(value = 'sum', key = { foo })
    def evict(String foo) {
        
    }
}
return TestService

''')
        when:
        def instance = testService.newInstance()
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__customCacheKeyGenerator" = keyGenerator
        instance.evict("a")

        then:
        1 * keyGenerator.generate("TestService", "evict", _, _ as Closure) >> "a"
        1 * cacheManager.getCache("sum") >> cache
        1 * cache.evict("a")
    }

    void "test evict all entries"() {
        given:
        GrailsCacheManager cacheManager = Mock(GrailsCacheManager)
        def cache = Mock(Cache)
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {

    @CacheEvict(value = 'sum', allEntries = true)
    def evict(String foo) {
        
    }
}
return TestService

''')
        when:
        def instance = testService.newInstance()
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        instance.evict("a")

        then:
        1 * cacheManager.getCache("sum") >> cache
        1 * cache.clear()
    }

    void "test evict with condition that evaluates to false"() {
        given:
        GrailsCacheManager cacheManager = Mock(GrailsCacheManager)
        def cache = Mock(Cache)
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {

    @CacheEvict(value = 'sum', allEntries = true, condition = { false })
    def evict(String foo) {
        
    }
}
return TestService

''')
        when:
        def instance = testService.newInstance()
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        instance.evict("a")

        then:
        0 * cacheManager.getCache("sum")
        0 * cache.clear()
    }

    void "test evict with condition that evaluates to true"() {
        given:
        GrailsCacheManager cacheManager = Mock(GrailsCacheManager)
        def cache = Mock(Cache)
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {

    @CacheEvict(value = 'sum', allEntries = true, condition = { true })
    def evict(String foo) {
        
    }
}
return TestService

''')
        when:
        def instance = testService.newInstance()
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        instance.evict("a")

        then:
        1 * cacheManager.getCache("sum") >> cache
        1 * cache.clear()
    }
}
