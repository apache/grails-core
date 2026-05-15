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
package org.grails.plugins.databasemigration.liquibase

import liquibase.parser.core.ParsedNode
import org.grails.plugins.databasemigration.DatabaseMigrationException
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

class DatabaseChangeLogBuilderSpec extends Specification {

    DatabaseChangeLogBuilder builder
    ApplicationContext applicationContext = Mock()

    def setup() {
        builder = new DatabaseChangeLogBuilder()
        builder.applicationContext = applicationContext
        builder.dataSourceName = 'testDataSource'
    }

    def 'builds simple nodes with attributes and values'() {
        when:
        ParsedNode root = (ParsedNode) builder.databaseChangeLog {
            changeSet(author: 'test', id: '1') {
                createTable(tableName: 'test_table') {
                    column(name: 'id', type: 'int')
                }
            }
        }

        then:
        root.name == 'databaseChangeLog'
        
        def changeSet = root.getChild(null, 'changeSet')
        changeSet != null
        changeSet.getChildValue(null, 'author') == 'test'
        changeSet.getChildValue(null, 'id') == '1'

        def createTable = changeSet.getChild(null, 'createTable')
        createTable != null
        createTable.getChildValue(null, 'tableName') == 'test_table'

        def column = createTable.getChild(null, 'column')
        column != null
        column.getChildValue(null, 'name') == 'id'
        column.getChildValue(null, 'type') == 'int'
    }

    def 'builds grailsChange node with special properties'() {
        given:
        Closure initClosure = { 'init' }
        Closure validateClosure = { 'validate' }
        Closure changeClosure = { 'change' }
        Closure rollbackClosure = { 'rollback' }

        when:
        ParsedNode root = (ParsedNode) builder.databaseChangeLog {
            changeSet(author: 'test', id: '1') {
                grailsChange {
                    init initClosure
                    validate validateClosure
                    change changeClosure
                    rollback rollbackClosure
                    confirm 'test confirmation'
                    checksum 'test checksum'
                }
            }
        }

        then:
        def changeSet = root.getChild(null, 'changeSet')
        def grailsChange = changeSet.getChild(null, 'grailsChange')
        grailsChange != null
        grailsChange.getChildValue(null, 'applicationContext') == applicationContext
        grailsChange.getChildValue(null, DATA_SOURCE_NAME_KEY) == 'testDataSource'

        grailsChange.getChildValue(null, 'init') == initClosure
        grailsChange.getChildValue(null, 'validate') == validateClosure
        grailsChange.getChildValue(null, 'change') == changeClosure
        grailsChange.getChildValue(null, 'rollback') == rollbackClosure
        grailsChange.getChildValue(null, 'confirm') == 'test confirmation'
        grailsChange.getChildValue(null, 'checksum') == 'test checksum'
    }

    def 'builds grailsPrecondition node'() {
        given:
        Closure checkClosure = { true }

        when:
        ParsedNode root = (ParsedNode) builder.databaseChangeLog {
            preConditions {
                grailsPrecondition {
                    check checkClosure
                }
            }
        }

        then:
        def preConditions = root.children[0]
        def grailsPrecondition = preConditions.children[0]
        grailsPrecondition.name == 'grailsPrecondition'
        grailsPrecondition.getChildValue(null, 'applicationContext') == applicationContext
        grailsPrecondition.getChildValue(null, DATA_SOURCE_NAME_KEY) == 'testDataSource'
        grailsPrecondition.getChildValue(null, 'check') == checkClosure
    }

    def 'throws DatabaseMigrationException for unknown methods in grailsChange'() {
        when:
        builder.databaseChangeLog {
            grailsChange {
                unknownMethod()
            }
        }

        then:
        thrown(DatabaseMigrationException)
    }

    def 'throws DatabaseMigrationException for unknown methods in grailsPrecondition'() {
        when:
        builder.databaseChangeLog {
            grailsPrecondition {
                unknownMethod()
            }
        }

        then:
        thrown(DatabaseMigrationException)
    }

    def 'handles nodes with values'() {
        when:
        ParsedNode root = (ParsedNode) builder.databaseChangeLog {
            someNode 'someValue'
        }

        then:
        root.children[0].name == 'someNode'
        root.children[0].value == 'someValue'
    }

    def 'handles nodes with attributes and values'() {
        when:
        ParsedNode root = (ParsedNode) builder.databaseChangeLog {
            someNode(attr: 'val', 'nodeValue')
        }

        then:
        def node = root.children[0]
        node.name == 'someNode'
        node.value == 'nodeValue'
        node.getChildValue(null, 'attr') == 'val'
    }
}
