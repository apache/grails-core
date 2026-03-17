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
package org.grails.plugins.databasemigration.liquibase

import spock.lang.Specification
import spock.lang.Unroll

class ChangelogXml2GroovySpec extends Specification {

    @Unroll
    void "test convert simple xml to groovy"() {
        given:
        def xml = """
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://www.liquibase.org/xml/ns/dbchangelog http://www.liquibase.org/xml/ns/dbchangelog/dbchangelog-3.1.xsd">
    <changeSet author="burt" id="1">
        <createTable tableName="test">
            <column name="id" type="int">
                <constraints primaryKey="true" nullable="false"/>
            </column>
        </createTable>
    </changeSet>
</databaseChangeLog>
"""
        when:
        String groovy = ChangelogXml2Groovy.convert(xml)

        then:
        groovy.contains('databaseChangeLog = {')
        groovy.contains('changeSet(author: "burt", id: "1") {')
        groovy.contains('createTable(tableName: "test") {')
        groovy.contains('column(name: "id", type: "int") {')
        groovy.contains('constraints(primaryKey: "true", nullable: "false")')
    }

    void "test convert with attributes and nesting"() {
        given:
        def xml = """
<databaseChangeLog>
    <property name="foo" value="bar"/>
    <changeSet author="beckwith" id="2">
        <addColumn tableName="test">
            <column name="new_col" type="varchar(255)"/>
        </addColumn>
    </changeSet>
</databaseChangeLog>
"""
        when:
        String groovy = ChangelogXml2Groovy.convert(xml)

        then:
        groovy.contains('property(name: "foo", value: "bar")')
        groovy.contains('changeSet(author: "beckwith", id: "2") {')
        groovy.contains('addColumn(tableName: "test") {')
        groovy.contains('column(name: "new_col", type: "varchar(255)")')
    }

    void "test escaping special characters"() {
        given:
        def xml = """
<databaseChangeLog>
    <changeSet author="test" id="3">
        <sql>select * from foo where name = '\$name' and path = 'C:\\\\temp'</sql>
    </changeSet>
</databaseChangeLog>
"""
        when:
        String groovy = ChangelogXml2Groovy.convert(xml)

        then:
        groovy.contains('sql("""select * from foo where name = \'\\$name\' and path = \'C:\\\\\\\\temp\'""")')
    }

    void "test empty changelog"() {
        given:
        def xml = "<databaseChangeLog></databaseChangeLog>"

        when:
        String groovy = ChangelogXml2Groovy.convert(xml)

        then:
        groovy.trim() == "databaseChangeLog = {\n}"
    }
}
