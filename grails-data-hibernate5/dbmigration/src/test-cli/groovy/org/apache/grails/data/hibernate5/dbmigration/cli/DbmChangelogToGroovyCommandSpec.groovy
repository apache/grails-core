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
package org.apache.grails.data.hibernate5.dbmigration.cli

import org.apache.grails.core.cli.ApplicationCommand
import org.grails.plugins.databasemigration.DatabaseMigrationException

class DbmChangelogToGroovyCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    static final String CHANGE_LOG = '''\
databaseChangeLog:
- changeSet:
    id: 1
    author: John Smith
    changes:
    - createTable:
        tableName: book
        columns:
        - column:
            name: id
            type: INT
'''

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmChangelogToGroovyCommand
    }

    def setup() {
        new File(changeLogLocation, 'changelog.yml').text = CHANGE_LOG
    }

    def "is the command dbm-changelog-to-groovy, with a description"() {
        expect:
            command.name == 'dbm-changelog-to-groovy'
            command.description == 'Converts a changelog file to a Groovy DSL file'
    }

    def "writes the changelog as Groovy to the file that is named"() {
        when:
            command.handle(getExecutionContext('changelog.yml', 'converted.groovy'))
            def converted = new File(changeLogLocation, 'converted.groovy').text.replaceAll(/\s/, '')

        then:
            converted.startsWith('databaseChangeLog={changeSet(author:"JohnSmith",id:"1"')
            converted.contains('{createTable(tableName:"book"){column(name:"id",type:"INT")')
    }

    def "writes the changelog as Groovy to the standard output when no file is named"() {
        when:
            command.handle(getExecutionContext('changelog.yml'))

        then:
            extractOutput(output).contains('createTable(tableName:"book")')
    }

    def "refuses a destination that is no Groovy file, and one that is there"() {
        when:
            command.handle(getExecutionContext('changelog.yml', 'converted.xml'))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message.endsWith('must be a Groovy file')

        when:
            new File(changeLogLocation, 'there.groovy').text = 'kept'
            command.handle(getExecutionContext('changelog.yml', 'there.groovy'))

        then:
            e = thrown(DatabaseMigrationException)
            e.message.endsWith('already exists!')
            new File(changeLogLocation, 'there.groovy').text == 'kept'
    }

    def "needs the name of the changelog to convert"() {
        when:
            command.handle(getExecutionContext())

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == 'The dbm-changelog-to-groovy command requires a source filename'
    }
}
