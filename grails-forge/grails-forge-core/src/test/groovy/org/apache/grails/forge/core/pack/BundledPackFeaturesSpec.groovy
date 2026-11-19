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
package org.apache.grails.forge.core.pack

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Feature
import org.grails.forge.feature.FeatureRegistry
import spock.lang.TempDir

class BundledPackFeaturesSpec extends ApplicationContextSpec {

    @TempDir
    Path directory

    void "every pack Forge names in its index is a feature of the framework"() {
        given:
        List<String> names = BundledPackFeatures.names(getClass().classLoader)
        FeatureRegistry registry = beanContext.getBean(FeatureRegistry)

        expect:
        names.contains('grails-web-console')
        names.unique(false) == names

        and:
        names.every { String name ->
            Feature feature = registry.all().find { it.name == name }
            feature instanceof PackFeature && ApplicationType.values().any { feature.supports(it) }
        }
    }

    void "a pack of Forge is read from its resources and is marked as one"() {
        when:
        Pack pack = new PackLoader().fromClasspath(getClass().classLoader, 'grails-web-console')

        then:
        pack.bundled
        pack.descriptor.name == 'grails-web-console'
        pack.origin == 'the features of Forge (grails-web-console)'
    }

    void "a pack Forge has not is said to be missing"() {
        when:
        new PackLoader().fromClasspath(getClass().classLoader, 'no-such-pack')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['There is no META-INF/grails-forge-bundled/no-such-pack/feature.yml, or it is larger than a descriptor can be']
    }

    void "a pack that is not of Forge cannot ask for the version Forge knows"() {
        given:
        Files.createDirectories(directory.resolve('templates'))
        Files.writeString(directory.resolve('feature.yml'), new String(
                getClass().classLoader.getResourceAsStream('META-INF/grails-forge-bundled/grails-web-console/feature.yml').readAllBytes(), 'UTF-8')
                .replace('name: grails-web-console', 'name: acme-console'))

        when:
        new PackLoader().fromDirectory(directory)

        then:
        PackDescriptorException e = thrown()
        e.problems == ["dependencies[0]: has the key 'versionOfForge', which this version of Forge does not read here; " +
                               'the keys are [scope, coordinates, version, excludes]']
    }

    void "a pack of Forge that asks for the version Forge knows names no version of its own"() {
        when:
        new PackDescriptorParser().parseOfForge(PackGenerationSpec.HEADER + 'name: acme\ndependencies:\n  - scope: implementation\n' +
                "    coordinates: com.acme:a\n    version: '1.0'\n    versionOfForge: true\n", 'test')

        then:
        PackDescriptorException e = thrown()
        e.problems == ['dependencies[0].versionOfForge: must be true, and then there is no version']
    }

    void "every pack of Forge gives the applications a compiled feature of the same name would"() {
        expect: 'the names here are the features that were compiled once; a pack that goes missing is a feature that goes missing'
        BundledPackFeatures.names(getClass().classLoader) == [
                'asciidoctor',
                'cache',
                'cache-ehcache',
                'grails-layout',
                'grails-quartz',
                'grails-spring-ai',
                'grails-web-console',
                'hibernate-validator',
                'openapi',
                'shade',
                'spring-ai-anthropic',
                'spring-ai-ollama',
                'spring-ai-openai']
    }

    void "a pack of Forge can name a page of the guide of this Grails in place of a link"() {
        given:
        String descriptor = PackGenerationSpec.HEADER + 'name: acme\nguideOfGrails: \'guide/REST.html#openApi\'\n'

        expect:
        new PackDescriptorParser().parseOfForge(descriptor, 'test').documentation ==
                "https://grails.apache.org/docs/${org.grails.forge.util.VersionInfo.documentationVersion}/guide/REST.html#openApi"

        when: 'the pack of a plugin cannot'
        new PackDescriptorParser().parse(descriptor, 'test')

        then:
        PackDescriptorException e = thrown()
        e.problems.size() == 1
        e.problems[0].contains("has the key 'guideOfGrails', which this version of Forge does not read here")
    }

    void "the page of the guide a pack of Forge names is a page of the guide: #page"() {
        when:
        new PackDescriptorParser().parseOfForge(PackGenerationSpec.HEADER + "name: acme\nguideOfGrails: '${page}'\n${more}", 'test')

        then:
        PackDescriptorException e = thrown()
        e.problems == [problem]

        where:
        page                          | more                                    || problem
        'https://evil.example/x.html' | ''                                      || "guideOfGrails: must be a page of the guide, such as 'guide/cache.html' or 'guide/REST.html#openApi', but is 'https://evil.example/x.html'"
        'guide/../../x.html'          | ''                                      || "guideOfGrails: must be a page of the guide, such as 'guide/cache.html' or 'guide/REST.html#openApi', but is 'guide/../../x.html'"
        'guide/cache.html'            | "documentation: 'https://a.example/'\n" || 'guideOfGrails: is in place of documentation, and both are given'
    }
}
