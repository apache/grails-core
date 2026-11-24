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

package org.grails.forge.build.dependencies

import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.gradle.GradleDependency
import org.grails.forge.options.DevelopmentReloading
import spock.lang.Specification
import spock.lang.Unroll

class GradleDependencyCapabilitySpec extends Specification {

    GeneratorContext ctx = Stub(GeneratorContext) {
        getDevelopmentReloading() >> DevelopmentReloading.DEVTOOLS
    }

    void "a dependency without capabilities selects the variant Gradle selects by default"() {
        expect:
        ehcache().build().capabilities == []
        snippet(ehcache()) == 'implementation "org.ehcache:ehcache"'
    }

    @Unroll
    void "capabilities are required in a closure on the dependency, before its exclusions: #expected"() {
        expect:
        snippet(dependency) == expected

        where:
        dependency                                                                                       || expected
        ehcache().requireCapability('org.ehcache:ehcache-jakarta')                                       || 'implementation("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") } }'
        ehcache().requireCapability('org.ehcache:ehcache-jakarta').requireCapability('com.acme:extra')   || 'implementation("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta"); requireCapability("com.acme:extra") } }'
        ehcache().version('3.12.0').requireCapability('org.ehcache:ehcache-jakarta')                     || 'implementation("org.ehcache:ehcache:3.12.0") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") } }'
        ehcache().exclude('org.glassfish.jaxb', 'jaxb-runtime').requireCapability('org.ehcache:ehcache-jakarta') ||
                'implementation("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") }; exclude group: "org.glassfish.jaxb", module: "jaxb-runtime" }'
        ehcache().runtimeOnly().requireCapability('org.ehcache:ehcache-jakarta')                         || 'runtimeOnly("org.ehcache:ehcache") { capabilities { requireCapability("org.ehcache:ehcache-jakarta") } }'
    }

    void "capabilities are kept when the scope of a dependency changes and when its coordinates are resolved"() {
        given:
        Dependency dependency = ehcache().requireCapability('org.ehcache:ehcache-jakarta').build()

        expect:
        dependency.scope(Scope.RUNTIME_ONLY).capabilities == ['org.ehcache:ehcache-jakarta']
        dependency.resolved(new DependencyCoordinate(dependency)).capabilities == ['org.ehcache:ehcache-jakarta']
    }

    void "a dependency made from a template has only its own capabilities"() {
        given:
        Dependency.Builder template = ehcache().requireCapability('org.ehcache:ehcache-jakarta').template()

        when:
        Dependency runtime = template.runtimeOnly().requireCapability('com.acme:extra').build()
        Dependency test = template.testImplementation().build()

        then:
        runtime.capabilities == ['org.ehcache:ehcache-jakarta', 'com.acme:extra']
        test.capabilities == ['org.ehcache:ehcache-jakarta']
    }

    void "the capabilities of a dependency cannot be changed"() {
        when:
        ehcache().requireCapability('org.ehcache:ehcache-jakarta').build().capabilities << 'com.acme:extra'

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    void "the capability '#capability' is rejected, as it is no 'group:name' that can be written in the build file"() {
        when:
        ehcache().requireCapability(capability)

        then:
        IllegalArgumentException e = thrown()
        e.message == "The capability of a dependency is 'group:name', but is '${capability}'"

        where:
        capability << ['org.ehcache', 'org.ehcache:', ':ehcache-jakarta', 'org.ehcache:ehcache:3.12.0', 'a:b")', 'a:b c']
    }

    void "a capability is required"() {
        when:
        ehcache().requireCapability(null)

        then:
        thrown(NullPointerException)
    }

    private static Dependency.Builder ehcache() {
        Dependency.builder().groupId('org.ehcache').artifactId('ehcache').implementation()
    }

    private String snippet(Dependency.Builder dependency) {
        new GradleDependency(dependency.build(), ctx).toSnippet()
    }
}
