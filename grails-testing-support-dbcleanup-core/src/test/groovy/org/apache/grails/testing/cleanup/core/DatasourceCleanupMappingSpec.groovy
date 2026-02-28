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

package org.apache.grails.testing.cleanup.core

import spock.lang.Specification

class DatasourceCleanupMappingSpec extends Specification {

    def "parse with null array returns cleanAll mapping"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(null)

        then:
        mapping.cleanAll
        mapping.entries.isEmpty()
    }

    def "parse with empty array returns cleanAll mapping"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(new String[0])

        then:
        mapping.cleanAll
        mapping.entries.isEmpty()
    }

    def "parse with plain datasource name creates entry without type"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(['dataSource'] as String[])

        then:
        !mapping.cleanAll
        mapping.entries.size() == 1
        mapping.entries[0].datasourceName == 'dataSource'
        !mapping.entries[0].hasExplicitType()
        mapping.entries[0].databaseType == null
    }

    def "parse with datasource:type creates entry with explicit type"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(['dataSource:h2'] as String[])

        then:
        !mapping.cleanAll
        mapping.entries.size() == 1
        mapping.entries[0].datasourceName == 'dataSource'
        mapping.entries[0].hasExplicitType()
        mapping.entries[0].databaseType == 'h2'
    }

    def "parse with multiple entries returns all"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(['dataSource:h2', 'dataSource_pg:postgresql'] as String[])

        then:
        !mapping.cleanAll
        mapping.entries.size() == 2
        mapping.entries[0].datasourceName == 'dataSource'
        mapping.entries[0].databaseType == 'h2'
        mapping.entries[1].datasourceName == 'dataSource_pg'
        mapping.entries[1].databaseType == 'postgresql'
    }

    def "parse with mixed entries (some with type, some without)"() {
        when:
        def mapping = DatasourceCleanupMapping.parse(['dataSource:h2', 'otherDs'] as String[])

        then:
        !mapping.cleanAll
        mapping.entries.size() == 2
        mapping.entries[0].datasourceName == 'dataSource'
        mapping.entries[0].hasExplicitType()
        mapping.entries[0].databaseType == 'h2'
        mapping.entries[1].datasourceName == 'otherDs'
        !mapping.entries[1].hasExplicitType()
    }

    def "parse trims whitespace from names and types"() {
        when:
        def mapping = DatasourceCleanupMapping.parse([' dataSource : h2 '] as String[])

        then:
        mapping.entries[0].datasourceName == 'dataSource'
        mapping.entries[0].databaseType == 'h2'
    }

    def "parse throws for null entry"() {
        when:
        DatasourceCleanupMapping.parse([null] as String[])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('null or empty')
    }

    def "parse throws for empty string entry"() {
        when:
        DatasourceCleanupMapping.parse([''] as String[])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('null or empty')
    }

    def "parse throws for blank entry"() {
        when:
        DatasourceCleanupMapping.parse(['  '] as String[])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('null or empty')
    }

    def "parse throws for empty datasource name before colon"() {
        when:
        DatasourceCleanupMapping.parse([':h2'] as String[])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('datasource name cannot be empty')
    }

    def "parse throws for empty database type after colon"() {
        when:
        DatasourceCleanupMapping.parse(['dataSource:'] as String[])

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains('database type cannot be empty')
    }

    def "entries list is immutable"() {
        given:
        def mapping = DatasourceCleanupMapping.parse(['dataSource:h2'] as String[])

        when:
        mapping.entries.add(new DatasourceCleanupMapping.Entry('x', 'y'))

        then:
        thrown(UnsupportedOperationException)
    }

    def "Entry toString shows name:type format for explicit type"() {
        given:
        def entry = new DatasourceCleanupMapping.Entry('dataSource', 'h2')

        expect:
        entry.toString() == 'dataSource:h2'
    }

    def "Entry toString shows name only for auto-discovered type"() {
        given:
        def entry = new DatasourceCleanupMapping.Entry('dataSource', null)

        expect:
        entry.toString() == 'dataSource'
    }
}
