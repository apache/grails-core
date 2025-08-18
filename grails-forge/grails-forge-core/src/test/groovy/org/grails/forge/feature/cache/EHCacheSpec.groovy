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
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.fixture.CommandOutputFixture
import spock.lang.Unroll

class EHCacheSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void 'test readme.md with feature cache-ehcache contains links to documentation'() {
        when:
        def output = generate(['cache-ehcache'])
        def readme = output["README.md"]

        then:
        readme
        readme.contains("[https://www.ehcache.org/](https://www.ehcache.org/)")
        readme.contains("[Grails EHCache Plugin documentation](https://grails-plugins.github.io/grails-cache-ehcache/latest/)")
    }

    @Unroll
    void 'test gradle cache-ehcache feature'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['cache-ehcache'])
                .render()

        then:
        template.contains('implementation "org.grails.plugins:cache-ehcache:3.0.0"')

    }

    void 'test cache-ehcache configuration'() {
        when:
        GeneratorContext commandContext = buildGeneratorContext(['cache-ehcache'])

        then:
        commandContext.configuration.get('grails.cache.ehcache.ehcacheXmlLocation'.toString()) == "classpath:ehcache.xml"
        commandContext.configuration.get('grails.cache.ehcache.lockTimeout'.toString()) == 200
    }

}
