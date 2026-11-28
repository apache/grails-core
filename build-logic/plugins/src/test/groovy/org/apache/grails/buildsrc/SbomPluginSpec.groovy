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

import java.util.jar.JarOutputStream

import groovy.json.JsonSlurper

import org.cyclonedx.Version
import org.cyclonedx.parsers.JsonParser
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

class SbomPluginSpec extends Specification {

    @TempDir
    File projectDir

    void "the sbom is valid in the CycloneDX 1.6 schema, also for a license from the license mapping"() {
        given: 'a library with a dependency mapped to a license id, one mapped to a license name and one with a preferred license'
        publishModule('org.jruby', 'jzlib', '1.1.5', 'BSD')
        publishModule('opensymphony', 'sitemesh', '2.6.0', 'The OpenSymphony Software License 1.1')
        publishModule('org.example', 'apache-licensed', '1.0', 'Apache License, Version 2.0')
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'library'"
        new File(projectDir, 'gradle.properties').text = 'projectVersion=1.0.0'
        new File(projectDir, 'build.gradle').text = '''
            plugins {
                id 'java-library'
                id 'org.apache.grails.buildsrc.sbom'
            }

            repositories {
                maven {
                    url = uri('repo')
                }
            }

            dependencies {
                implementation 'org.jruby:jzlib:1.1.5'
                implementation 'opensymphony:sitemesh:2.6.0'
                implementation 'org.example:apache-licensed:1.0'
            }
        '''

        when: 'the sbom is built'
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments('cyclonedxBom', '--offline', '--stacktrace')
                .withPluginClasspath()
                .build()
        final sbom = new File(projectDir, 'build/library-1.0.0-sbom.json').text

        then: 'the CycloneDX schema finds no error in it'
        new JsonParser().validate(sbom, Version.VERSION_16).empty

        and: 'each dependency has its license under the key license'
        final licenses = licensesByName(sbom)
        licenses.jzlib == [[license: [id: 'BSD-3-Clause', url: 'https://opensource.org/license/bsd-3-clause/']]]
        licenses.sitemesh == [[license: [
                name: 'The OpenSymphony Software License, Version 1.1',
                url : 'https://raw.githubusercontent.com/sitemesh/sitemesh2/refs/heads/master/LICENSE.txt',
        ]]]
        licenses['apache-licensed']*.license*.id == ['Apache-2.0']
    }

    private void publishModule(String group, String name, String version, String licenseName) {
        final moduleDir = new File(projectDir, "repo/${group.replace('.', '/')}/$name/$version")
        moduleDir.mkdirs()
        new File(moduleDir, "${name}-${version}.pom").text = """\
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>$group</groupId>
                <artifactId>$name</artifactId>
                <version>$version</version>
                <licenses>
                    <license>
                        <name>$licenseName</name>
                    </license>
                </licenses>
            </project>
        """.stripIndent()
        new JarOutputStream(new FileOutputStream(new File(moduleDir, "${name}-${version}.jar"))).close()
    }

    private static Map<String, List<Map>> licensesByName(String sbom) {
        final components = (new JsonSlurper().parseText(sbom) as Map).components as List<Map>
        components.collectEntries { [(it.name): it.licenses] }
    }
}
