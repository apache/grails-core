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
package org.grails.forge.features.neo4j

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.GormImpl
import org.grails.forge.options.JdkVersion
import org.grails.forge.utils.CommandSpec

class Neo4jGormBuildSpec extends CommandSpec {

    void 'an application with GORM for Neo4j #description resolves the class paths it runs and tests on'() {
        given:
        generateProject(OperatingSystem.LINUX, features, ApplicationType.WEB,
                DevelopmentReloading.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, gormImpl)

        when: 'Gradle reports the dependencies of every configuration, resolving each of them'
        BuildResult result = executeGradle('dependencies')
        String output = result.output.normalize()

        then: 'nothing on the class path that bootRun and bootJar use fails to resolve'
        String runtimeClasspath = configurationReport(output, 'runtimeClasspath')
        !runtimeClasspath.contains('FAILED')

        and: 'GORM for Neo4j gets the Neo4j driver it is built for'
        runtimeClasspath =~ /neo4j-java-driver\S* -> 4\.4\.13\b/

        and: 'nothing on the class path of the tests fails to resolve'
        String testRuntimeClasspath = configurationReport(output, 'testRuntimeClasspath')
        !testRuntimeClasspath.contains('FAILED')
        testRuntimeClasspath =~ /neo4j-java-driver\S* -> 4\.4\.13\b/

        when: 'the application is packaged with its runtime class path'
        result = executeGradle('bootJar')

        then:
        result.task(':bootJar').outcome == TaskOutcome.SUCCESS

        where:
        description        | features       | gormImpl
        'alone'            | []             | GormImpl.NEO4J
        'and Hibernate 5'  | ['gorm-neo4j'] | GormImpl.HIBERNATE5
    }

    /**
     * Returns the part of the output of the dependencies task that reports the given configuration:
     * from its heading to the blank line that ends it.
     */
    private static String configurationReport(String output, String configuration) {
        int start = output.indexOf("\n${configuration} - ")
        assert start >= 0, "The dependencies report has no ${configuration}:\n${output}"
        int end = output.indexOf('\n\n', start + 1)
        output.substring(start + 1, end < 0 ? output.length() : end)
    }

    @Override
    String getTempDirectoryPrefix() {
        'testneo4j'
    }
}
