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
import org.grails.forge.options.TestFramework
import spock.lang.Specification

class GradleDependencyComparatorSpec extends Specification {

    void "sort based on gradle configuration"() {
        given:
        def ctx = Stub(GeneratorContext) {
            getTestFramework() >> TestFramework.JUNIT
        }
        List<GradleDependency> dependencies =
                [dep(Dependency.builder().groupId("io.micronaut").artifactId("micronaut-validation").implementation(), ctx),
                 dep(Dependency.builder().groupId("io.swagger.core.v3").artifactId("swagger-annotations").implementation(), ctx),
                 dep(Dependency.builder().groupId("io.micronaut").artifactId("micronaut-runtime").implementation(), ctx),
                 dep(Dependency.builder().groupId("jakarta.annotation").artifactId("jakarta.annotation-api").implementation(), ctx),
                 dep(Dependency.builder().groupId("io.micronaut").artifactId("micronaut-http-client").implementation(), ctx),
                 dep(Dependency.builder().groupId("io.micronaut.openapi").artifactId("micronaut-openapi").annotationProcessor(), ctx),
                 dep(Dependency.builder().groupId("io.micronaut.sql").artifactId("micronaut-jdbc-hikari").implementation(), ctx),
                 dep(Dependency.builder().groupId("org.apache.grails").artifactId("grails-console").console(), ctx),
                 dep(Dependency.builder().groupId("org.testcontainers").artifactId("testcontainers").testImplementation(), ctx),
                 dep(Dependency.builder().groupId("mysql").artifactId("mysql-connector-java").runtimeOnly(), ctx),
                 dep(Dependency.builder().groupId("org.testcontainers").artifactId("junit-jupiter").testImplementation(), ctx),
                 dep(Dependency.builder().groupId("org.testcontainers").artifactId("mysql").testImplementation(), ctx),
                 dep(Dependency.builder().groupId("ch.qos.logback").artifactId("logback-classic").runtimeOnly(), ctx)]

        when:
        dependencies.sort(GradleDependency.COMPARATOR)

        then:
        "${str(dependencies[0])}" == 'implementation "io.micronaut:micronaut-http-client"'
        "${str(dependencies[1])}" == 'implementation "io.micronaut:micronaut-runtime"'
        "${str(dependencies[2])}" == 'implementation "io.micronaut:micronaut-validation"'
        "${str(dependencies[3])}" == 'implementation "io.micronaut.sql:micronaut-jdbc-hikari"'
        "${str(dependencies[4])}" == 'implementation "io.swagger.core.v3:swagger-annotations"'
        "${str(dependencies[5])}" == 'implementation "jakarta.annotation:jakarta.annotation-api"'
        "${str(dependencies[6])}" == 'compileOnly "io.micronaut.openapi:micronaut-openapi"'
        "${str(dependencies[7])}" == 'console "org.apache.grails:grails-console"'
        "${str(dependencies[8])}" == 'runtimeOnly "ch.qos.logback:logback-classic"'
        "${str(dependencies[9])}" == 'runtimeOnly "mysql:mysql-connector-java"'
        "${str(dependencies[10])}" == 'testImplementation "org.testcontainers:junit-jupiter"'
        "${str(dependencies[11])}" == 'testImplementation "org.testcontainers:mysql"'
        "${str(dependencies[12])}" == 'testImplementation "org.testcontainers:testcontainers"'
    }

    private static String str(GradleDependency dependency) {
        "${dependency.getConfiguration().toString()} \"${dependency.groupId}:${dependency.artifactId}\""
    }

    private static GradleDependency dep(Dependency.Builder dependency, GeneratorContext ctx) {
        new GradleDependency(dependency.build(), ctx)
    }
}
