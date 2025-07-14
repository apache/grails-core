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

import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import org.grails.plugin.cache.GrailsCacheManager
import org.springframework.cache.Cache
import spock.lang.Specification

/**
 * Created by graemerocher on 18/04/2017.
 */
class CacheableParseSpec extends Specification {

    void "test declare condition in closure"() {
        given:
        Class testService = new GroovyShell().evaluate('''
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {
    @Cacheable(value = 'basic', condition = { x < 10 })
    def multiply(int x, int y) {
        x * y
    }
    
    @Cacheable(value = 'sum')
    def sum(int x, int y) {
        x + y
    }
}
return TestService

''')
        def instance = testService.newInstance()
        expect:
        instance.multiply(1,2) ==2
    }

    void "test include tenant id when used with @CurrentTenant"() {
        given:
        def config = [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                      (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()]
        def datastore = new SimpleMapDatastore(config, getClass().getPackage())

        Class testService = new GroovyShell().evaluate('''

import grails.gorm.multitenancy.CurrentTenant
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {
    @Cacheable('basic')
    @CurrentTenant
    def multiply(int x, int y) {
        x * y
    }
}
return TestService

''')
        def instance = testService.newInstance()
        GrailsCacheManager cacheManager = Stub(GrailsCacheManager)
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        GrailsCacheKeyGenerator keyGenerator = Mock(GrailsCacheKeyGenerator)
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__customCacheKeyGenerator" = keyGenerator

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test")
        instance.multiply(1,2)
        cacheManager.getCache("basic") >> Stub(Cache)

        then:
        1 * keyGenerator.generate("TestService", "multiply", _, [x:1, y:2, tenantId: "test"])

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        datastore?.close()
    }

    void "test include tenant id when used with @CurrentTenant and @Transactional"() {
        given:
        def config = [(Settings.SETTING_MULTI_TENANCY_MODE): MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR,
                      (Settings.SETTING_MULTI_TENANT_RESOLVER): new SystemPropertyTenantResolver()]
        def datastore = new SimpleMapDatastore(config, getClass().getPackage())

        Class testService = new GroovyShell().evaluate('''

import grails.gorm.multitenancy.CurrentTenant
import grails.gorm.transactions.Transactional
import grails.plugin.cache.*
import groovy.transform.CompileStatic

@CompileStatic
class TestService {
    @Cacheable('basic')
    @CurrentTenant
    @Transactional
    def multiply(int x, int y) {
        x * y
    }
}
return TestService

''')
        def instance = testService.newInstance()
        GrailsCacheManager cacheManager = Stub(GrailsCacheManager)
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__grailsCacheManager" = cacheManager
        GrailsCacheKeyGenerator keyGenerator = Mock(GrailsCacheKeyGenerator)
        instance.@"org_grails_plugin_cache_GrailsCacheManagerAware__customCacheKeyGenerator" = keyGenerator

        when:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test")
        cacheManager.getCache("basic") >> Stub(Cache)
        instance.multiply(1,2)

        then:
        1 * keyGenerator.generate("TestService", "multiply", _, [x:1, y:2, tenantId: "test"])

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
        datastore?.close()
    }

    void "test CacheTransform for a service with @Transactional annotation"() {
        when:
        def gcl = new GroovyClassLoader()
        def service = gcl.parseClass('''
import grails.gorm.transactions.Transactional
import grails.plugin.cache.Cacheable

@Transactional
class TestService {

    @Cacheable(value = "demo", key = {-> name })
    String test(String name) {
        name
    }
}

''')

        then: "The criteria contains the correct criterion"
        service.newInstance()
    }
}
