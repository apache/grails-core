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

class GradleDependencyExclusionSpec extends Specification {

    GeneratorContext ctx = Stub(GeneratorContext) {
        getDevelopmentReloading() >> DevelopmentReloading.DEVTOOLS
    }

    void "a dependency without exclusions is declared as before"() {
        expect:
        snippet(Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation()) ==
                'implementation "com.acme:acme-audit"'
    }

    @Unroll
    void "exclusions are declared in a closure on the dependency: #expected"() {
        expect:
        snippet(dependency) == expected

        where:
        dependency                                                                                  || expected
        acme().exclude('org.slf4j', 'slf4j-simple')                                                 || 'implementation("com.acme:acme-audit") { exclude group: "org.slf4j", module: "slf4j-simple" }'
        acme().exclude('org.slf4j', null)                                                           || 'implementation("com.acme:acme-audit") { exclude group: "org.slf4j" }'
        acme().exclude('org.slf4j', 'slf4j-simple').exclude('commons-logging', 'commons-logging')   || 'implementation("com.acme:acme-audit") { exclude group: "org.slf4j", module: "slf4j-simple"; exclude group: "commons-logging", module: "commons-logging" }'
        acme().version('2.3.0').exclude('org.slf4j', 'slf4j-simple')                                || 'implementation("com.acme:acme-audit:2.3.0") { exclude group: "org.slf4j", module: "slf4j-simple" }'
        acme().pom(true).exclude('org.slf4j', 'slf4j-simple')                                       || 'implementation(platform("com.acme:acme-audit")) { exclude group: "org.slf4j", module: "slf4j-simple" }'
        acme().pom(true).enforced(true).exclude('org.slf4j', 'slf4j-simple')                        || 'implementation(enforcedPlatform("com.acme:acme-audit")) { exclude group: "org.slf4j", module: "slf4j-simple" }'
        acme().integrationTestImplementationTestFixtures().exclude('org.slf4j', 'slf4j-simple')     || 'integrationTestImplementation(testFixtures("com.acme:acme-audit")) { exclude group: "org.slf4j", module: "slf4j-simple" }'
    }

    void "exclusions are kept when the scope of a dependency changes"() {
        given:
        Dependency dependency = acme().exclude('org.slf4j', 'slf4j-simple').build()

        expect:
        dependency.scope(Scope.RUNTIME_ONLY).exclusions == [new DependencyExclusion('org.slf4j', 'slf4j-simple')]
        new GradleDependency(dependency.scope(Scope.RUNTIME_ONLY), ctx).toSnippet() ==
                'runtimeOnly("com.acme:acme-audit") { exclude group: "org.slf4j", module: "slf4j-simple" }'
    }

    void "a dependency made from a template has only its own exclusions"() {
        given:
        Dependency.Builder template = acme().exclude('org.slf4j', 'slf4j-simple').template()

        when:
        Dependency runtime = template.runtimeOnly().exclude('commons-logging', null).build()
        Dependency test = template.testImplementation().build()

        then:
        runtime.exclusions*.toString() == ['org.slf4j:slf4j-simple', 'commons-logging']
        test.exclusions*.toString() == ['org.slf4j:slf4j-simple']
    }

    void "the comment on a dependency follows its exclusions"() {
        expect:
        snippet(Dependency.builder().groupId('org.springframework.boot').artifactId('spring-boot-devtools').developmentOnly()
                .exclude('org.slf4j', 'slf4j-simple')) ==
                'developmentOnly("org.springframework.boot:spring-boot-devtools") { exclude group: "org.slf4j", module: "slf4j-simple" }' +
                ' // Spring Boot DevTools may cause performance slowdowns or compatibility issues on larger applications'
    }

    @Unroll
    void "an exclusion of group '#group' and artifact '#artifact' is rejected"() {
        when:
        acme().exclude(group, artifact)

        then:
        thrown(IllegalArgumentException)

        where:
        group       | artifact
        ''          | 'slf4j-simple'
        ' '         | null
        'org.slf4j' | ''
    }

    void "an exclusion needs a group"() {
        when:
        acme().exclude(null, 'slf4j-simple')

        then:
        thrown(NullPointerException)
    }

    private static Dependency.Builder acme() {
        Dependency.builder().groupId('com.acme').artifactId('acme-audit').implementation()
    }

    private String snippet(Dependency.Builder dependency) {
        new GradleDependency(dependency.build(), ctx).toSnippet()
    }
}
