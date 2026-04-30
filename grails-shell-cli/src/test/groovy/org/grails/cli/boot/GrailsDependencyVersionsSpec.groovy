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
package org.grails.cli.boot

import groovy.grape.GrapeEngine
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class GrailsDependencyVersionsSpec extends Specification {

    @TempDir
    Path tempDir

    private URI writePom(String filename, String content) {
        Path pomFile = tempDir.resolve(filename)
        Files.writeString(pomFile, content)
        return pomFile.toUri()
    }

    def "addDependencyManagement parses direct dependencies from a BOM POM"() {
        given: "A GrailsDependencyVersions with a mock grape engine that returns a simple BOM"
        String pomXml = '''\
            <project>
                <properties>
                    <grails-core.version>7.0.11</grails-core.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>${grails-core.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-web</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''
        URI bomUri = writePom('grails-bom.pom', pomXml)
        GrapeEngine grape = Mock(GrapeEngine)
        grape.resolve(null, _) >> [bomUri]

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: "Direct dependencies are resolved including property-based versions"
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
        versions.find('org.apache.grails', 'grails-web') != null
        versions.find('org.apache.grails', 'grails-web').version == '7.0.11'
    }

    def "addDependencyManagement recursively resolves imported BOMs"() {
        given: "A base BOM with profile dependencies and a parent BOM that imports it"
        String baseBomXml = '''\
            <project>
                <properties>
                    <grails-profile-web.version>7.0.11</grails-profile-web.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>web</artifactId>
                            <version>${grails-profile-web.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>rest-api</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''

        String parentBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>7.0.11</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-web-mvc</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''

        URI baseBomUri = writePom('grails-base-bom.pom', baseBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed with the parent BOM"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: "The parent BOM is resolved first"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: "The imported base BOM is resolved recursively"
        1 * grape.resolve(null, { it.module == 'grails-base-bom' && it.type == 'pom' }) >> [baseBomUri]

        and: "Direct dependencies from the parent BOM are available"
        versions.find('org.apache.grails', 'grails-web-mvc') != null
        versions.find('org.apache.grails', 'grails-web-mvc').version == '7.0.11'

        and: "Dependencies from the imported base BOM are also available"
        versions.find('org.apache.grails.profiles', 'web') != null
        versions.find('org.apache.grails.profiles', 'web').version == '7.0.11'
        versions.find('org.apache.grails.profiles', 'rest-api') != null
        versions.find('org.apache.grails.profiles', 'rest-api').version == '7.0.11'
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
    }

    def "addDependencyManagement gracefully handles unresolvable imported BOMs"() {
        given: "A BOM that imports another BOM which cannot be resolved"
        String parentBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.example</groupId>
                            <artifactId>missing-bom</artifactId>
                            <version>1.0.0</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''

        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)
        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: "The parent BOM is resolved"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: "The missing imported BOM resolution throws but is caught"
        1 * grape.resolve(null, { it.module == 'missing-bom' }) >> { throw new RuntimeException("Not found") }

        and: "Direct dependencies from the parent BOM are still available"
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
    }

    def "addDependencyManagement without grapeEngine skips imported BOMs without error"() {
        given: "A GrailsDependencyVersions with no grape engine and a BOM with an import"
        String parentBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>7.0.11</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''

        URI bomUri = writePom('grails-bom.pom', parentBomXml)
        GrapeEngine grape = Mock(GrapeEngine)
        grape.resolve(null, { it.module == 'grails-bom' }) >> [bomUri]

        when: "GrailsDependencyVersions is constructed and then grapeEngine is cleared"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: "The imported BOM is attempted to be resolved via the grape engine"
        1 * grape.resolve(null, { it.module == 'grails-base-bom' && it.type == 'pom' }) >> { throw new RuntimeException("Not found") }

        and: "Direct dependencies are still resolved"
        versions.find('org.apache.grails', 'grails-core') != null
    }
}
