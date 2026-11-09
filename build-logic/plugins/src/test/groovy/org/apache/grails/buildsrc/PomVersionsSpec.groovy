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

package org.apache.grails.buildsrc

import java.nio.file.Path

import org.gradle.api.GradleException
import spock.lang.Specification
import spock.lang.TempDir

import org.apache.maven.model.Model

/**
 * Reads the version a BOM's own entries give a module, as {@code grails-hibernate7-bom} reads the
 * Hibernate ORM version from {@code spring-boot-dependencies}:
 *
 * <pre>
 * test:parent-bom:1.0, with test:grand-parent:1.0 as its parent
 *   test:lib-a                    ${lib-a.version} = 1.0
 *   test:lib-literal              5.0
 *   ${project.groupId}:lib-own    ${project.version}
 *   test:lib-inherited            ${inherited.version} - declared only by the parent POM
 *   other:lib-other               2.0
 *   test:lib-literal:tests        5.0 - the same artifact again, with a classifier
 *   test:family-bom               ${family-bom.version} = 3.0, imported, and managing test:family-core
 *   test:test-maven-plugin        1.0 - in pluginManagement
 * </pre>
 */
class PomVersionsSpec extends Specification {

    @TempDir
    Path testDir

    Model bom

    def setup() {
        File pom = testDir.resolve('parent-bom-1.0.pom').toFile()
        pom.text = '''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent><groupId>test</groupId><artifactId>grand-parent</artifactId><version>1.0</version></parent>
    <groupId>test</groupId>
    <artifactId>parent-bom</artifactId>
    <version>1.0</version>
    <packaging>pom</packaging>
    <properties>
        <lib-a.version>1.0</lib-a.version>
        <family-bom.version>3.0</family-bom.version>
    </properties>
    <dependencyManagement>
        <dependencies>
            <dependency><groupId>test</groupId><artifactId>lib-a</artifactId><version>${lib-a.version}</version></dependency>
            <dependency><groupId>test</groupId><artifactId>lib-literal</artifactId><version>5.0</version></dependency>
            <dependency><groupId>${project.groupId}</groupId><artifactId>lib-own</artifactId><version>${project.version}</version></dependency>
            <dependency><groupId>test</groupId><artifactId>lib-inherited</artifactId><version>${inherited.version}</version></dependency>
            <dependency><groupId>other</groupId><artifactId>lib-other</artifactId><version>2.0</version></dependency>
            <dependency><groupId>test</groupId><artifactId>lib-literal</artifactId><version>5.0</version><classifier>tests</classifier></dependency>
            <dependency>
                <groupId>test</groupId><artifactId>family-bom</artifactId><version>${family-bom.version}</version>
                <type>pom</type><scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
    <build>
        <pluginManagement>
            <plugins>
                <plugin><groupId>test</groupId><artifactId>test-maven-plugin</artifactId><version>1.0</version></plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
'''
        bom = PomVersions.read(pom)
    }

    def "an entry's version is resolved from the BOM's own properties and the Maven built-ins"() {
        expect:
        PomVersions.managedVersion(bom, module) == version

        where:
        module             || version
        'test:lib-a'       || '1.0'
        'test:lib-literal' || '5.0'
        'test:lib-own'     || '1.0'
    }

    def "a module the BOM has no entry for has no version"() {
        expect:
        PomVersions.managedVersion(bom, 'test:lib-unknown') == null
    }

    def "the BOMs it imports are not read, so a module only an imported BOM manages has no version"() {
        expect: 'test:family-core, which only test:family-bom manages, and the imported BOM itself'
        PomVersions.managedVersion(bom, 'test:family-core') == null
        PomVersions.managedVersion(bom, 'test:family-bom') == null
    }

    def "an entry whose version only the parent POM declares fails instead of going unread"() {
        when:
        PomVersions.managedVersion(bom, 'test:lib-inherited')

        then:
        GradleException e = thrown()
        e.message == 'Cannot resolve test:lib-inherited:${inherited.version}, managed by test:parent-bom:1.0, from that POM\'s own properties.'
    }

    def "the artifacts of a group are the ones the BOM's own entries list, in their order and each once"() {
        expect: 'its own group resolved, and neither the BOM it imports, the plugin it manages nor the other group'
        PomVersions.managedArtifactIds(bom, 'test') == ['lib-a', 'lib-literal', 'lib-own', 'lib-inherited']
        PomVersions.managedArtifactIds(bom, 'other') == ['lib-other']
    }

    def "a group the BOM manages nothing of has no artifacts, whatever it shares a prefix with"() {
        expect:
        PomVersions.managedArtifactIds(bom, 'tes').isEmpty()
        PomVersions.managedArtifactIds(bom, 'unknown').isEmpty()
    }
}
