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
package org.grails.forge.features.cache

import java.nio.file.Files
import javax.xml.parsers.DocumentBuilderFactory

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.utils.CommandSpec
import org.w3c.dom.Element
import spock.lang.Shared
import spock.lang.Unroll

/**
 * Runs an application generated with {@code cache-ehcache}: a service of it caches with the annotations of the
 * Cache plugin, and its integration tests, which start the application, say that a call is served from a cache of
 * Ehcache that {@code grails-app/conf/ehcache.xml} configures, reached through JCache, and that a cache it does not
 * configure is created from its default template.
 */
class EhcacheBuildSpec extends CommandSpec {

    private static final String PACKAGE_PATH = 'example/grails'

    private static final String SERVICE = '''\
        package example.grails

        import java.util.concurrent.ConcurrentHashMap
        import java.util.concurrent.atomic.AtomicInteger

        import grails.plugin.cache.Cacheable

        class GreetingService {

            final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>()

            @Cacheable('example')
            String greet(String name) {
                "Hello ${name}, call ${called(name)}".toString()
            }

            // a cache that ehcache.xml does not configure, of a value that cannot be serialized
            @Cacheable('notConfigured')
            Greeting greeting(String name) {
                new Greeting(text: "Hello ${name}, call ${called(name)}")
            }

            static class Greeting {
                String text
            }

            int callsOf(String name) {
                calls.get(name)?.get() ?: 0
            }

            private int called(String name) {
                calls.computeIfAbsent(name) { new AtomicInteger() }.incrementAndGet()
            }
        }
        '''.stripIndent()

    private static final String SPEC = '''\
        package example.grails

        import javax.cache.CacheManager

        import org.ehcache.config.CacheRuntimeConfiguration
        import org.ehcache.config.ResourceType
        import org.ehcache.jsr107.Eh107Configuration
        import org.ehcache.jsr107.EhcacheCachingProvider
        import org.springframework.beans.factory.annotation.Autowired
        import org.springframework.context.ApplicationContext
        import org.springframework.cache.jcache.JCacheCache
        import spock.lang.Specification

        import grails.testing.mixin.integration.Integration

        @Integration
        class EhcacheCachingSpec extends Specification {

            @Autowired
            GreetingService greetingService

            @Autowired
            ApplicationContext applicationContext

            void 'the second call is served from the cache of ehcache.xml, an Ehcache cache reached through JCache'() {
                when:
                String first = greetingService.greet('configured')
                String second = greetingService.greet('configured')
                Object grailsCacheManager = applicationContext.getBean('grailsCacheManager')
                javax.cache.Cache<Object, Object> cache = nativeCacheOf(grailsCacheManager, 'example')

                then:
                first == 'Hello configured, call 1'
                second == first
                greetingService.callsOf('configured') == 1

                and: 'the cache manager of the plugin is the one of the application, over the JCache cache manager of Ehcache'
                grailsCacheManager instanceof JCacheGrailsCacheManager
                applicationContext.getBean('jCacheManager', CacheManager).cachingProvider instanceof EhcacheCachingProvider

                and: 'the value is in the Ehcache cache that ehcache.xml configures, which keeps 100 entries'
                cache.unwrap(org.ehcache.Cache) != null
                heapOf(cache) == 100
                cache.collect { javax.cache.Cache.Entry<Object, Object> entry -> entry.value } == [first]
            }

            void 'a cache that ehcache.xml does not configure is created from its default template, and keeps a value as it is'() {
                when:
                GreetingService.Greeting first = greetingService.greeting('created')
                GreetingService.Greeting second = greetingService.greeting('created')
                javax.cache.Cache<Object, Object> cache = nativeCacheOf(applicationContext.getBean('grailsCacheManager'), 'notConfigured')

                then:
                second.is(first)
                greetingService.callsOf('created') == 1

                and: 'it has the template of ehcache.xml, which keeps 1000 entries'
                heapOf(cache) == 1000
            }

            void 'the caches of the tags of the plugin are created when the application starts'() {
                expect:
                applicationContext.getBean('grailsCacheManager').cacheExists('grailsBlocksCache')
                applicationContext.getBean('grailsCacheManager').cacheExists('grailsTemplatesCache')
            }

            private static javax.cache.Cache<Object, Object> nativeCacheOf(Object grailsCacheManager, String name) {
                (javax.cache.Cache<Object, Object>) ((JCacheCache) grailsCacheManager.getCache(name)).nativeCache
            }

            private static long heapOf(javax.cache.Cache<Object, Object> cache) {
                cache.getConfiguration(Eh107Configuration).unwrap(CacheRuntimeConfiguration).resourcePools
                        .getPoolForResource(ResourceType.Core.HEAP).size
            }
        }
        '''.stripIndent()

    /** One Gradle user home for the applications, so that their dependencies are downloaded once */
    @Shared
    File userHome

    void setupSpec() {
        userHome = Files.createTempDirectory("${tempDirectoryPrefix}TestKit").toFile()
        gradleRunner.withTestKitDir(userHome)
    }

    void cleanupSpec() {
        userHome?.deleteDir()
    }

    @Unroll
    void 'an application with cache-ehcache and #gormImpl caches with Ehcache through JCache'(GormImpl gormImpl) {
        given:
        generateProject(OperatingSystem.LINUX, ['cache-ehcache'], ApplicationType.WEB, DevelopmentReloading.DEFAULT_OPTION,
                JdkVersion.DEFAULT_OPTION, gormImpl)
        write("grails-app/services/${PACKAGE_PATH}/GreetingService.groovy", SERVICE)
        write("src/integration-test/groovy/${PACKAGE_PATH}/EhcacheCachingSpec.groovy", SPEC)

        when: 'the integration tests of the cache run, and only those: the functional test of the application needs a browser in a container'
        BuildResult result = executeGradle('integrationTest', '--tests', 'example.grails.EhcacheCachingSpec')

        then:
        result.task(':integrationTest').outcome == TaskOutcome.SUCCESS

        and: 'every feature method ran and passed'
        ['tests', 'failures', 'errors', 'skipped'].collectEntries { String count -> [(count): suite().getAttribute(count)] } ==
                [tests: '3', failures: '0', errors: '0', skipped: '0']

        when: 'the runtime class path is resolved'
        List<String> runtime = executeGradle('dependencies', '--configuration', 'runtimeClasspath').output.readLines()
        String declared = runtime.find { String line -> line ==~ /[+\\]--- org\.glassfish\.jaxb:jaxb-runtime -> .*/ }
        String version = declared?.substring(declared.indexOf(' -> ') + 4)?.replace(' (*)', '')

        then: 'Ehcache asks for jaxb-runtime with a range, which would have the build list the versions in the repository: nothing asks for it with a range'
        !runtime.any { String line -> line.contains('org.glassfish.jaxb:jaxb-runtime:[') || line.contains('[3,3.1)') }

        and: 'the application declares jaxb-runtime without a version, and it has the version the BOM constrains it to'
        version ==~ /\d+\.\d+\.\d+/
        runtime.any { String line -> line.endsWith("--- org.glassfish.jaxb:jaxb-runtime:${version} (c)") }

        and: 'with the API of JAXB of Jakarta EE that Ehcache reads its XML with'
        runtime.any { String line -> line.contains('--- jakarta.xml.bind:jakarta.xml.bind-api') }

        where:
        gormImpl << [GormImpl.HIBERNATE5, GormImpl.HIBERNATE7]
    }

    @Override
    String getTempDirectoryPrefix() {
        'testehcache'
    }

    private Element suite() {
        DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new File(dir, 'build/test-results/integrationTest/TEST-example.grails.EhcacheCachingSpec.xml'))
                .documentElement
    }

    private void write(String path, String text) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.setText(text, 'UTF-8')
    }
}
