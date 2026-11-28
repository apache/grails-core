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

package org.grails.forge.feature.cache

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.Options
import spock.lang.Unroll

class EHCacheSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test readme.md with feature cache-ehcache contains links to documentation'() {
        when:
        def output = generate(['cache-ehcache'])
        def readme = output["README.md"]

        then:
        readme
        readme.contains("[https://www.ehcache.org/](https://www.ehcache.org/)")
        readme.contains('/guide/cache.html#cacheEhcache')
        !readme.contains('grails-plugins.github.io/grails-cache-ehcache')
    }

    void 'the build has Ehcache in its Jakarta EE variant and the Cache plugin, in the versions the BOM manages, and no plugin of its own'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['cache-ehcache'])
                .render()

        then: 'Ehcache without the jaxb-runtime it asks for with a range, and jaxb-runtime in the version the BOM manages'
        template.contains('implementation("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") }; ' +
                'exclude group: "org.glassfish.jaxb", module: "jaxb-runtime" }')
        template.contains('runtimeOnly "org.glassfish.jaxb:jaxb-runtime"\n')
        template.contains('implementation "org.apache.grails:grails-cache"')
        !template.contains('org.grails.plugins:cache-ehcache')
        !template.contains('javax.cache')
    }

    void 'the application defines grailsCacheManager over the caches of ehcache.xml'() {
        when:
        Map<String, String> output = generate(['cache-ehcache'])
        String resources = output['grails-app/conf/spring/resources.groovy']
        String manager = output['src/main/groovy/example/grails/JCacheGrailsCacheManager.groovy']
        String xml = output['grails-app/conf/ehcache.xml']

        then: 'resources.groovy defines the JCache cache manager of ehcache.xml and the cache manager of the plugin over it'
        resources.contains('import org.springframework.cache.jcache.JCacheManagerFactoryBean')
        resources.contains('import example.grails.JCacheGrailsCacheManager')
        resources.contains("jCacheManager(JCacheManagerFactoryBean) {\n        cacheManagerUri = 'classpath:ehcache.xml'\n    }")
        resources.contains("grailsCacheManager(JCacheGrailsCacheManager, ref('jCacheManager'))")

        and: 'the cache manager is a GrailsCacheManager that creates a cache it does not have, keeping its values as they are'
        manager.startsWith('package example.grails\n')
        manager.contains('class JCacheGrailsCacheManager extends JCacheCacheManager implements GrailsCacheManager {')
        manager.contains('new MutableConfiguration<Object, Object>().setStoreByValue(false)')
        !manager.contains('Licensed to the Apache Software Foundation')

        and: 'the configuration of Ehcache has the default template of JCache and an example cache'
        xml.startsWith('<?xml version="1.0" encoding="UTF-8"?>\n')
        xml.contains('<jsr107:defaults default-template="default"/>')
        xml.contains('<cache-template name="default">')
        xml.contains('<cache alias="example" uses-template="default">')
        !xml.contains('Licensed to the Apache Software Foundation')
    }

    void 'without cache-ehcache resources.groovy defines no bean and there is no Ehcache'() {
        when:
        Map<String, String> output = generate(['cache'])

        then:
        output['grails-app/conf/spring/resources.groovy'] == '// Place your Spring DSL code here\nbeans = {\n}'
        !output.containsKey('grails-app/conf/ehcache.xml')
        !output.keySet().any { String path -> path.endsWith('JCacheGrailsCacheManager.groovy') }
    }

    void 'test cache-ehcache configuration'() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['cache-ehcache'])

        then: 'the Cache plugin is configured, but chooses no cache manager, and nothing configures the plugin of Ehcache that is not there'
        commandContext.configuration.get('grails.cache.enabled') == true
        !commandContext.configuration.containsKey('grails.cache.cacheManager')
        !commandContext.configuration.keySet().any { String key -> key.startsWith('grails.cache.ehcache') }
    }

    void 'the Cache plugin alone still chooses its cache manager, by the name the plugin compares'() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['cache'])

        then:
        commandContext.configuration.get('grails.cache.cacheManager') == 'GrailsConcurrentMapCacheManager'
    }

    @Unroll
    void 'cache-ehcache is offered for #type, an application'() {
        expect:
        generate(type, ['cache-ehcache']).containsKey('grails-app/conf/ehcache.xml')

        where:
        type << [ApplicationType.WEB, ApplicationType.REST_API]
    }

    @Unroll
    void 'cache-ehcache is not offered for #type, which leaves its cache provider to the application that uses it'() {
        when:
        generate(type, new Options(), ['cache-ehcache'])

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('cache-ehcache')

        where:
        type << [ApplicationType.PLUGIN, ApplicationType.WEB_PLUGIN, ApplicationType.REST_API_PLUGIN]
    }

}
