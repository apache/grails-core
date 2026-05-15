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

import liquibase.Scope
import liquibase.database.Database
import liquibase.executor.Executor
import liquibase.executor.ExecutorService
import liquibase.parser.core.ParsedNode
import liquibase.resource.ResourceAccessor
import liquibase.statement.SqlStatement
import org.springframework.context.ApplicationContext
import spock.lang.Specification

import static org.grails.plugins.databasemigration.PluginConstants.DATA_SOURCE_NAME_KEY

class GroovyChangeSpec extends Specification {

    GroovyChange change
    ApplicationContext applicationContext = Mock()
    Database database = Mock()
    ExecutorService executorService = Mock()
    Executor executor = Mock()

    def setup() {
        change = new GroovyChange()
        change.ctx = applicationContext
        
        // Mocking Scope and ExecutorService is tricky because it's a singleton in Liquibase
        // For simple tests we might not need shouldRun() to return true if we don't trigger it
    }

    def 'load correctly populates fields from ParsedNode'() {
        given:
        ParsedNode parsedNode = Mock()
        ResourceAccessor resourceAccessor = Mock()
        Closure init = { -> }
        Closure validate = { -> }
        Closure changeClosure = { -> }
        Closure rollback = { -> }

        when:
        change.load(parsedNode, resourceAccessor)

        then:
        1 * parsedNode.getChildValue(null, 'applicationContext', ApplicationContext) >> applicationContext
        1 * parsedNode.getChildValue(null, DATA_SOURCE_NAME_KEY, String) >> 'dataSource_myDb'
        1 * parsedNode.getChildValue(null, 'init', Closure) >> init
        1 * parsedNode.getChildValue(null, 'validate', Closure) >> validate
        1 * parsedNode.getChildValue(null, 'change', Closure) >> changeClosure
        1 * parsedNode.getChildValue(null, 'rollback', Closure) >> rollback
        1 * parsedNode.getChildValue(null, 'confirm', String) >> 'Confirmed!'
        1 * parsedNode.getChildValue(null, 'checksum', String) >> 'mychecksum'

        change.ctx == applicationContext
        change.dataSourceName == 'myDb'
        change.initClosure == init
        change.validateClosure == validate
        change.changeClosure == changeClosure
        change.rollbackClosure == rollback
        change.confirmationMessage == 'Confirmed!'
        change.checksumString == 'mychecksum'
    }

    def 'finishInitialization executes initClosure'() {
        given:
        boolean called = false
        change.initClosure = { -> called = true }

        when:
        change.finishInitialization()

        then:
        called
        change.initClosureCalled
    }

    def 'validate executes validateClosure and collects errors'() {
        given:
        change.validateClosure = { -> delegate.error('error 1') }
        // We need shouldRun() to be true. In Liquibase Scope it defaults to true if not LoggingExecutor.
        // If it fails due to Scope, we might need to mock Scope.

        when:
        def errors = change.validate(database)

        then:
        errors.hasErrors()
        errors.errorMessages.contains('error 1')
        change.validateClosureCalled
    }

    def 'generateStatements executes changeClosure and returns statements'() {
        given:
        SqlStatement stmt = Mock()
        // We override shouldRun to avoid Liquibase Scope issues in unit test
        GroovyChange changeSpy = Spy(GroovyChange) {
            shouldRun() >> true
            withNewTransaction(_) >> { Closure c -> c.call() }
        }
        changeSpy.ctx = applicationContext
        changeSpy.changeClosure = { -> delegate.sqlStatement(stmt) }

        when:
        def stmts = changeSpy.generateStatements(database)

        then:
        stmts.length == 1
        stmts[0] == stmt
        changeSpy.changeClosureCalled
    }

    def 'supportsRollback returns true if not in logging mode'() {
        given:
        GroovyChange changeSpy = Spy(GroovyChange) {
            shouldRun() >> true
        }

        expect:
        changeSpy.supportsRollback(database)
    }
}
