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
package org.apache.grails.forge.core.bom

import org.grails.forge.feature.grails.GrailsBase
import org.grails.forge.util.VersionInfo
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The tables of what the BOMs of Grails manage, which the build of this module writes from the poms
 * the BOMs are published with.
 */
class BomVersionsSpec extends Specification {

    @Unroll
    void "Forge is built with what #bom manages"() {
        when:
        BomVersions versions = BomVersions.of(bom)

        then: 'the artifacts of Grails and its plugins, in the version of this Grails, and what the BOM pins of others'
        versions.coordinates == "org.apache.grails:${bom}"
        versions.versions.size() > 200
        versions.versionOf('org.apache.grails:grails-core') == VersionInfo.grailsVersion
        versions.versionOf('org.apache.grails:grails-cache') == VersionInfo.grailsVersion
        versions.versionOf('org.apache.grails:grails-spring-security') == VersionInfo.grailsVersion
        versions.versionOf('org.apache.grails:grails-mail') == VersionInfo.grailsVersion
        versions.versionOf('com.acme:acme-audit-grails') == null

        and: 'every entry is an artifact with one version, with no property left in it'
        versions.versions.every { String artifact, String version -> artifact ==~ /[^:\s]+:[^:\s]+/ && version ==~ /[^\s$]+/ }

        and: 'what the BOM imports from elsewhere is not in the table: nothing was resolved to write it'
        versions.versionOf('org.springframework.boot:spring-boot') == null

        where:
        bom << BomVersions.SHIPPED
    }

    void "the tables of grails-bom and grails-hibernate7-bom differ in what Grails Data for Hibernate 7 needs"() {
        expect:
        BomVersions.of('grails-bom').versionOf('org.hibernate.orm:hibernate-core') == null
        BomVersions.of('grails-hibernate7-bom').versionOf('org.hibernate.orm:hibernate-core') != null
        BomVersions.of('grails-bom').versionOf('org.hibernate:hibernate-core-jakarta') != null
        BomVersions.of('grails-hibernate7-bom').versionOf('org.hibernate:hibernate-core-jakarta') == null
    }

    void "the table of grails-neo4j-bom has what grails-bom, which it imports, manages, and the Neo4j driver GORM for Neo4j is built with"() {
        given:
        BomVersions neo4j = BomVersions.of('grails-neo4j-bom')
        BomVersions grails = BomVersions.of('grails-bom')

        expect: 'every version of grails-bom, and two more'
        neo4j.versions.subMap(grails.versions.keySet()) == grails.versions
        (neo4j.versions.keySet() - grails.versions.keySet()) == ['org.neo4j.driver:neo4j-java-driver', 'org.neo4j.test:neo4j-harness'] as Set

        and: 'the driver at the version GORM for Neo4j needs, older than the one Spring Boot manages, and what Hibernate 5 needs, as grails-bom has it'
        neo4j.versionOf('org.neo4j.driver:neo4j-java-driver') ==~ /4\.4\.\d+/
        neo4j.versionOf('org.hibernate:hibernate-core-jakarta') == grails.versionOf('org.hibernate:hibernate-core-jakarta')
        neo4j.versionOf('org.hibernate.orm:hibernate-core') == null
        grails.versionOf('org.neo4j.driver:neo4j-java-driver') == null
    }

    @Unroll
    void "there is a table for the BOM the generator gives an application with #features, #bom, when this build makes it"() {
        expect:
        GrailsBase.bomArtifactId(features) == bom
        (BomVersions.of(bom) != null) == shipped

        where:
        features                                  || bom                               | shipped
        []                                        || 'grails-bom'                      | true
        ['gorm-hibernate5']                       || 'grails-bom'                      | true
        ['gorm-hibernate7']                       || 'grails-hibernate7-bom'           | true
        ['gorm-neo4j']                            || 'grails-neo4j-bom'                | true
        ['gorm-hibernate5', 'gorm-neo4j']         || 'grails-neo4j-bom'                | true
        ['gorm-mongodb']                          || 'grails-bom'                      | true
        ['grails-micronaut']                      || 'grails-micronaut-bom'            | false
        ['grails-micronaut', 'gorm-hibernate7']   || 'grails-hibernate7-micronaut-bom' | false
    }

    void "a name that is no BOM this build makes has no table"() {
        expect:
        BomVersions.of('../grails-bom') == null
        BomVersions.of('grails-hibernate5-bom') == null
    }
}
