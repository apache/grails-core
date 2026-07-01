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

    // These specs verify the BOM parsing and import-resolution logic, not any particular release.
    // The versions injected into the synthetic POMs below are arbitrary placeholders defined per
    // feature; assertions compare against those same placeholders rather than hard-coded release
    // numbers, so the specs stay valid no matter how the real managed versions change over time.

    @TempDir
    Path tempDir

    private URI writePom(String filename, String content) {
        Path pomFile = tempDir.resolve(filename)
        Files.writeString(pomFile, content)
        return pomFile.toUri()
    }

    def "addDependencyManagement parses direct dependencies from a BOM POM"() {
        given: "A GrailsDependencyVersions with a mock grape engine that returns a simple BOM"
        String coreVersion = '1.0.0'
        String webVersion = '2.0.0'
        String pomXml = """\
            <project>
                <properties>
                    <grails-core.version>${coreVersion}</grails-core.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>\${grails-core.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-web</artifactId>
                            <version>${webVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """
        URI bomUri = writePom('grails-bom.pom', pomXml)
        GrapeEngine grape = Mock(GrapeEngine)
        grape.resolve(null, _) >> [bomUri]

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: coreVersion, type: 'pom'])

        then: "Direct dependencies are resolved including property-based versions"
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == coreVersion
        versions.find('org.apache.grails', 'grails-web') != null
        versions.find('org.apache.grails', 'grails-web').version == webVersion
    }

    def "addDependencyManagement recursively resolves imported Grails BOMs"() {
        given: "A base BOM with profile dependencies and a parent BOM that imports it"
        String profileWebVersion = '1.0.0'
        String restApiVersion = '2.0.0'
        String coreVersion = '3.0.0'
        String webMvcVersion = '4.0.0'
        String baseBomVersion = '5.0.0'
        String baseBomXml = """\
            <project>
                <properties>
                    <grails-profile-web.version>${profileWebVersion}</grails-profile-web.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>web</artifactId>
                            <version>\${grails-profile-web.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>rest-api</artifactId>
                            <version>${restApiVersion}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>${coreVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>${baseBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-web-mvc</artifactId>
                            <version>${webMvcVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI baseBomUri = writePom('grails-base-bom.pom', baseBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed with the parent BOM"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: baseBomVersion, type: 'pom'])

        then: "The parent BOM is resolved first"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: "The imported base BOM is resolved recursively"
        1 * grape.resolve(null, { it.module == 'grails-base-bom' && it.type == 'pom' }) >> [baseBomUri]

        and: "Direct dependencies from the parent BOM are available"
        versions.find('org.apache.grails', 'grails-web-mvc') != null
        versions.find('org.apache.grails', 'grails-web-mvc').version == webMvcVersion

        and: "Dependencies from the imported base BOM are also available"
        versions.find('org.apache.grails.profiles', 'web') != null
        versions.find('org.apache.grails.profiles', 'web').version == profileWebVersion
        versions.find('org.apache.grails.profiles', 'rest-api') != null
        versions.find('org.apache.grails.profiles', 'rest-api').version == restApiVersion
        versions.find('org.apache.grails', 'grails-core') != null
        versions.find('org.apache.grails', 'grails-core').version == coreVersion
    }

    def "addDependencyManagement recurses into third-party imported BOMs so Spring Boot managed versions are surfaced"() {
        given: "A Grails base BOM that imports spring-boot-dependencies and pins its own Groovy"
        String webVersion = '1.0.0'
        String grailsGroovyVersion = '2.0.0'
        String springBootVersion = '3.0.0'
        String baseBomVersion = '4.0.0'
        String webMvcVersion = '5.0.0'
        String mongoVersion = '6.0.0'
        String springBootGroovyVersion = '7.0.0'
        String grailsBaseBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails.profiles</groupId>
                            <artifactId>web</artifactId>
                            <version>${webVersion}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy</artifactId>
                            <version>${grailsGroovyVersion}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>${springBootVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String springBootBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>${mongoVersion}</version>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy</artifactId>
                            <version>${springBootGroovyVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>${baseBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-web-mvc</artifactId>
                            <version>${webMvcVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI grailsBaseBomUri = writePom('grails-base-bom.pom', grailsBaseBomXml)
        URI springBootBomUri = writePom('spring-boot-dependencies.pom', springBootBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: baseBomVersion, type: 'pom'])

        then: "The parent and Grails base BOMs are resolved"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]
        1 * grape.resolve(null, { it.module == 'grails-base-bom' }) >> [grailsBaseBomUri]

        and: "The third-party Spring Boot BOM is now resolved too"
        1 * grape.resolve(null, { it.module == 'spring-boot-dependencies' && it.type == 'pom' }) >> [springBootBomUri]

        and: "Versions managed only by Spring Boot are surfaced"
        versions.find('org.mongodb', 'mongodb-driver-sync') != null
        versions.find('org.mongodb', 'mongodb-driver-sync').version == mongoVersion

        and: "Grails' own pinned version wins over the Spring Boot managed version"
        versions.find('org.apache.groovy', 'groovy').version == grailsGroovyVersion
        versions.find('org.apache.groovy', 'groovy').version != springBootGroovyVersion

        and: "Grails dependencies remain available"
        versions.find('org.apache.grails', 'grails-web-mvc') != null
        versions.find('org.apache.grails.profiles', 'web') != null
    }

    def "addDependencyManagement resolves a repeated third-party BOM import only once"() {
        given: "Both the parent and base Grails BOMs import the same spring-boot-dependencies"
        String springBootVersion = '1.0.0'
        String mongoVersion = '2.0.0'
        String baseBomVersion = '3.0.0'
        String grailsBaseBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>${springBootVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String springBootBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.mongodb</groupId>
                            <artifactId>mongodb-driver-sync</artifactId>
                            <version>${mongoVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>${baseBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>${springBootVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI grailsBaseBomUri = writePom('grails-base-bom.pom', grailsBaseBomXml)
        URI springBootBomUri = writePom('spring-boot-dependencies.pom', springBootBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: baseBomVersion, type: 'pom'])

        then: "The parent and base BOMs are each resolved once"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]
        1 * grape.resolve(null, { it.module == 'grails-base-bom' }) >> [grailsBaseBomUri]

        and: "The duplicated spring-boot-dependencies import is resolved exactly once"
        1 * grape.resolve(null, { it.module == 'spring-boot-dependencies' }) >> [springBootBomUri]

        and: "Its managed version is available"
        versions.find('org.mongodb', 'mongodb-driver-sync').version == mongoVersion
    }

    def "addDependencyManagement resolves BOMs imported transitively by a third-party BOM"() {
        given: "A parent BOM importing spring-boot-dependencies, which itself imports junit-bom"
        String springBootVersion = '1.0.0'
        String junitBomVersion = '2.0.0'
        String junitApiVersion = '3.0.0'
        String junitBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter-api</artifactId>
                            <version>${junitApiVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String springBootBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.junit</groupId>
                            <artifactId>junit-bom</artifactId>
                            <version>${junitBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>${springBootVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI junitBomUri = writePom('junit-bom.pom', junitBomXml)
        URI springBootBomUri = writePom('spring-boot-dependencies.pom', springBootBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: springBootVersion, type: 'pom'])

        then: "The parent BOM and the BOMs it transitively imports are all resolved"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]
        1 * grape.resolve(null, { it.module == 'spring-boot-dependencies' }) >> [springBootBomUri]
        1 * grape.resolve(null, { it.module == 'junit-bom' }) >> [junitBomUri]

        and: "A version managed only by the nested third-party BOM is surfaced"
        versions.find('org.junit.jupiter', 'junit-jupiter-api') != null
        versions.find('org.junit.jupiter', 'junit-jupiter-api').version == junitApiVersion
    }

    def "versionProperties keeps the Grails BOM value when a recursed third-party BOM declares the same property"() {
        given: "A Grails BOM that declares groovy.version and imports a Spring Boot BOM declaring a conflicting groovy.version"
        // CreateAppCommand stamps a newly created app's build from versionProperties (e.g.
        // groovy.version, gorm.version, grails-gradle-plugins.version). Recursing into
        // spring-boot-dependencies - which declares its own groovy.version - means the Grails
        // value must still win, otherwise `grails create-app` would stamp the wrong Groovy.
        String grailsGroovyVersion = '1.0.0'
        String springBootGroovyVersion = '2.0.0'
        String springBootVersion = '3.0.0'
        String springBootBomXml = """\
            <project>
                <properties>
                    <groovy.version>${springBootGroovyVersion}</groovy.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.groovy</groupId>
                            <artifactId>groovy</artifactId>
                            <version>\${groovy.version}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        String parentBomXml = """\
            <project>
                <properties>
                    <groovy.version>${grailsGroovyVersion}</groovy.version>
                </properties>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-dependencies</artifactId>
                            <version>${springBootVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI springBootBomUri = writePom('spring-boot-dependencies.pom', springBootBomXml)
        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)

        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: grailsGroovyVersion, type: 'pom'])

        then: "The parent BOM and the imported Spring Boot BOM are resolved"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]
        1 * grape.resolve(null, { it.module == 'spring-boot-dependencies' && it.type == 'pom' }) >> [springBootBomUri]

        and: "versionProperties keeps the Grails-declared groovy.version (first-writer-wins)"
        versions.versionProperties['groovy.version'] == grailsGroovyVersion
        versions.versionProperties['groovy.version'] != springBootGroovyVersion
    }

    def "addDependencyManagement fails when an imported BOM cannot be resolved"() {
        given: "A BOM that imports a Grails BOM which cannot be resolved"
        String missingBomVersion = '1.0.0'
        String coreVersion = '2.0.0'
        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-missing-bom</artifactId>
                            <version>${missingBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>${coreVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        URI parentBomUri = writePom('grails-bom.pom', parentBomXml)
        GrapeEngine grape = Mock(GrapeEngine)

        when: "GrailsDependencyVersions is constructed"
        new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: coreVersion, type: 'pom'])

        then: "The parent BOM is resolved"
        1 * grape.resolve(null, { it.module == 'grails-bom' }) >> [parentBomUri]

        and: "The missing Grails BOM resolution throws"
        1 * grape.resolve(null, { it.module == 'grails-missing-bom' }) >> { throw new RuntimeException("Not found") }

        and: "The failure is surfaced instead of silently dropping managed versions"
        def e = thrown(IllegalStateException)
        e.message == 'Failed to resolve imported BOM org.apache.grails:grails-missing-bom:1.0.0'
        e.cause.message == 'Not found'
    }

    def "addDependencyManagement without grapeEngine skips imported BOMs without error"() {
        given: "A BOM with an import and a mock grape engine"
        String bootstrapVersion = '1.0.0'
        String coreVersion = '2.0.0'
        String baseBomVersion = '3.0.0'
        String simpleBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-bootstrap</artifactId>
                            <version>${bootstrapVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """
        URI simpleBomUri = writePom('simple-bom.pom', simpleBomXml)
        GrapeEngine grape = Mock(GrapeEngine)
        grape.resolve(null, _) >> [simpleBomUri]

        and: "A GrailsDependencyVersions instance with grapeEngine then set to null"
        def versions = new GrailsDependencyVersions(grape, [group: 'org.apache.grails', module: 'grails-bom', version: bootstrapVersion, type: 'pom'])
        versions.@grapeEngine = null

        and: "A POM with a Grails BOM import"
        String parentBomXml = """\
            <project>
                <dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-base-bom</artifactId>
                            <version>${baseBomVersion}</version>
                            <type>pom</type>
                            <scope>import</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.apache.grails</groupId>
                            <artifactId>grails-core</artifactId>
                            <version>${coreVersion}</version>
                        </dependency>
                    </dependencies>
                </dependencyManagement>
            </project>
        """

        when: "addDependencyManagement is called with null grapeEngine"
        def pom = new groovy.xml.XmlSlurper().parseText(parentBomXml)
        versions.addDependencyManagement(pom)

        then: "No exception is thrown and direct dependencies are resolved"
        noExceptionThrown()
        versions.find('org.apache.grails', 'grails-core') != null
    }
}
