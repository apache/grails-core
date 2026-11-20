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

import grails.config.ConfigMap
import liquibase.changelog.ChangeLogParameters
import liquibase.exception.ChangeLogParseException
import liquibase.parser.core.ParsedNode
import liquibase.resource.InputStreamList
import liquibase.resource.ResourceAccessor
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class GroovyChangeLogParserSpec extends Specification {

    GroovyChangeLogParser parser
    ResourceAccessor resourceAccessor = Mock()
    ApplicationContext applicationContext = Mock()
    ConfigMap config = Mock()

    def setup() {
        parser = new GroovyChangeLogParser()
        parser.applicationContext = applicationContext
        parser.config = config
    }

    def "supports groovy files"() {
        expect:
        parser.supports("changelog.groovy", resourceAccessor)
        !parser.supports("changelog.xml", resourceAccessor)
        !parser.supports("changelog.sql", resourceAccessor)
    }

    def "parses a simple groovy changelog to ParsedNode"() {
        given:
        String changelogText = """
databaseChangeLog = {
    changeSet(author: "test", id: "1") {
        createTable(tableName: "test_table") {
            column(name: "id", type: "int")
        }
    }
}
"""
        String location = "changelog.groovy"
        ChangeLogParameters changeLogParameters = Mock()
        InputStream inputStream = new ByteArrayInputStream(changelogText.getBytes("UTF-8"))
        InputStreamList inputStreamList = new InputStreamList(new URI("file:" + location), inputStream)

        when:
        ParsedNode node = parser.parseToNode(location, changeLogParameters, resourceAccessor)

        then:
        1 * resourceAccessor.openStreams(null, location) >> inputStreamList
        1 * config.getProperty('changelogProperties', Map) >> [:]
        node != null
        node.name == "databaseChangeLog"
        node.children.size() == 1
        node.children[0].name == "changeSet"
        node.children[0].getChildValue(null, "author") == "test"
        node.children[0].getChildValue(null, "id") == "1"
    }

    def "parses groovy changelog with properties"() {
        given:
        String changelogText = """
databaseChangeLog = {
    changeSet(author: authorName, id: "1") {
        addColumn(tableName: "test_table") {
            column(name: "new_col", type: "varchar(255)")
        }
    }
}
"""
        String location = "changelog.groovy"
        ChangeLogParameters changeLogParameters = Mock()
        InputStream inputStream = new ByteArrayInputStream(changelogText.getBytes("UTF-8"))
        InputStreamList inputStreamList = new InputStreamList(new URI("file:" + location), inputStream)

        when:
        ParsedNode node = parser.parseToNode(location, changeLogParameters, resourceAccessor)

        then:
        1 * resourceAccessor.openStreams(null, location) >> inputStreamList
        1 * config.getProperty('changelogProperties', Map) >> [authorName: "John Doe"]
        1 * changeLogParameters.set("authorName", "John Doe", null, null, null, true, null)
        node != null
        node.children[0].getChildValue(null, "author") == "John Doe"
    }

    def "parses groovy changelog with complex property map"() {
        given:
        String changelogText = """
databaseChangeLog = {
    property(name: "foo", value: propValue)
}
"""
        String location = "changelog.groovy"
        ChangeLogParameters changeLogParameters = Mock()
        InputStream inputStream = new ByteArrayInputStream(changelogText.getBytes("UTF-8"))
        InputStreamList inputStreamList = new InputStreamList(new URI("file:" + location), inputStream)

        when:
        parser.parseToNode(location, changeLogParameters, resourceAccessor)

        then:
        1 * resourceAccessor.openStreams(null, location) >> inputStreamList
        1 * config.getProperty('changelogProperties', Map) >> [propValue: [value: "bar", contexts: "test", labels: "l1", databases: "h2"]]
        1 * changeLogParameters.set("propValue", "bar", "test", "l1", "h2", true, null)
    }

    def "throws ChangeLogParseException on invalid script"() {
        given:
        String changelogText = "this is not valid groovy"
        String location = "changelog.groovy"
        InputStream inputStream = new ByteArrayInputStream(changelogText.getBytes("UTF-8"))
        InputStreamList inputStreamList = new InputStreamList(new URI("file:" + location), inputStream)

        when:
        parser.parseToNode(location, new ChangeLogParameters(), resourceAccessor)

        then:
        1 * resourceAccessor.openStreams(null, location) >> inputStreamList
        1 * config.getProperty('changelogProperties', Map) >> [:]
        thrown(ChangeLogParseException)
    }

    def "throws ChangeLogParseException when openStreams is empty"() {
        given:
        String location = "missing.groovy"
        InputStreamList inputStreamList = new InputStreamList()

        when:
        parser.parseToNode(location, new ChangeLogParameters(), resourceAccessor)

        then:
        1 * resourceAccessor.openStreams(null, location) >> inputStreamList
        def e = thrown(ChangeLogParseException)
        e.message.contains("Could not find physicalChangeLogLocation: missing.groovy")
    }
}
