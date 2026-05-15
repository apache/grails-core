/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
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

    def 'addDependencyManagement parses direct dependencies from a BOM POM'() {
        given: 'A GrailsDependencyVersions with a mock grape engine that returns a simple BOM'
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

        when: 'GrailsDependencyVersions is constructed'
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: 'Direct dependencies are resolved including property-based versions'
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
        versions.find('org.apache.grails', 'grails-web') != null
        versions.find('org.apache.grails', 'grails-web').version == '7.0.11'
    }

    def 'addDependencyManagement recursively resolves imported Grails BOMs'() {
        given: 'A base BOM with profile dependencies and a parent BOM that imports it'
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

        when: 'GrailsDependencyVersions is constructed with the parent BOM'
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: 'The parent BOM is resolved first'
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: 'The imported base BOM is resolved recursively'
        1 * grape.resolve(null, { it.module == 'grails-base-bom' && it.type == 'pom' }) >> [baseBomUri]

        and: 'Direct dependencies from the parent BOM are available'
        versions.find('org.apache.grails', 'grails-web-mvc') != null
        versions.find('org.apache.grails', 'grails-web-mvc').version == '7.0.11'

        and: 'Dependencies from the imported base BOM are also available'
        versions.find('org.apache.grails.profiles', 'web') != null
        versions.find('org.apache.grails.profiles', 'web').version == '7.0.11'
        versions.find('org.apache.grails.profiles', 'rest-api') != null
        versions.find('org.apache.grails.profiles', 'rest-api').version == '7.0.11'
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
    }

    def 'addDependencyManagement does not recurse into third-party imported BOMs'() {
        given: 'A BOM that imports both a Grails BOM and third-party BOMs'
        String grailsBaseBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>web</artifactId>
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
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>3.5.0</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy-bom</artifactId>
                            <version>4.0.30</version>
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

        URI grailsBaseBomUri = writePom('grails-base-bom.pom', grailsBaseBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: 'GrailsDependencyVersions is constructed'
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: 'The parent BOM is resolved'
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: 'The Grails base BOM is resolved'
        1 * grape.resolve(null, { it.module == 'grails-base-bom' }) >> [grailsBaseBomUri]

        and: 'Third-party BOMs are never resolved'
        0 * grape.resolve(null, { it.module == 'spring-boot-dependencies' })
        0 * grape.resolve(null, { it.module == 'groovy-bom' })

        and: 'Dependencies from both Grails BOMs are available'
        versions.find('org.apache.grails', 'grails-web-mvc') != null
        versions.find('org.apache.grails.profiles', 'web') != null
    }

    def 'addDependencyManagement gracefully handles unresolvable imported Grails BOMs'() {
        given: 'A BOM that imports a Grails BOM which cannot be resolved'
        String parentBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-missing-bom</artifactId>
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

        when: 'GrailsDependencyVersions is constructed'
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])

        then: 'The parent BOM is resolved'
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: 'The missing Grails BOM resolution throws but is caught'
        1 * grape.resolve(null, { it.module == 'grails-missing-bom' }) >> { throw new RuntimeException('Not found') }

        and: 'Direct dependencies from the parent BOM are still available'
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == '7.0.11'
    }

    def 'addDependencyManagement without grapeEngine skips imported BOMs without error'() {
        given: 'A BOM with an import and a mock grape engine'
        String simpleBomXml = '''\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-bootstrap</artifactId>
                            <version>7.0.11</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        '''
        URI simpleBomUri = writePom('simple-bom.pom', simpleBomXml)
        GrapeEngine grape = Mock(GrapeEngine)
        grape.resolve(null, _) >> [simpleBomUri]

        and: 'A GrailsDependencyVersions instance with grapeEngine then set to null'
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: '7.0.11', type: 'pom'])
        versions.@grapeEngine = null

        and: 'A POM with a Grails BOM import'
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

        when: 'addDependencyManagement is called with null grapeEngine'
        def pom = new groovy.xml.XmlSlurper().parseText(parentBomXml)
        versions.addDependencyManagement(pom)

        then: 'No exception is thrown and direct dependencies are resolved'
        noExceptionThrown()
        versions.find('org.apache.grails', 'grails-core') != null
    }
}
