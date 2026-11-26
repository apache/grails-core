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

import org.apache.grails.core.cli.ApplicationTool
import org.grails.plugins.databasemigration.DatabaseMigrationException

class DbmCreateChangelogToolSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationTool> getCommandClass() {
        return DbmCreateChangelogTool
    }

    def "is the command dbm-create-changelog, with a description"() {
        expect:
            command.name == 'dbm-create-changelog'
            command.description == 'Creates an empty changelog file'
    }

    def "creates an empty changelog file in the format its name gives"() {
        when:
            command.handle(getExecutionContext(filename))

        then:
            new File(changeLogLocation, filename).text.replaceAll(/\s/, '') == expected

        where:
            filename           | expected
            'empty.groovy'     | 'databaseChangeLog={}'
            'empty.yml'        | 'databaseChangeLog:[]'
    }

    def "keeps a file that is there unless it is told to overwrite it"() {
        given:
            def file = new File(changeLogLocation, 'there.groovy')
            file.text = 'kept'

        when:
            command.handle(getExecutionContext('there.groovy'))

        then:
            def e = thrown(DatabaseMigrationException)
            e.message.startsWith('ChangeLogFile ')
            e.message.endsWith('there.groovy already exists!')
            file.text == 'kept'

        when:
            command.handle(getExecutionContext('there.groovy', '--force'))

        then:
            file.text.replaceAll(/\s/, '') == 'databaseChangeLog={}'
    }

    def "adds the new file to the changelog of the application when asked to"() {
        given:
            command.changeLogFile.text = 'databaseChangeLog = {\n}\n'

        when:
            command.handle(getExecutionContext('added.groovy', '--add'))

        then:
            new File(changeLogLocation, 'added.groovy').isFile()
            command.changeLogFile.text.contains("include file: 'added.groovy'")
    }

    def "creates the file in a directory that is not there yet"() {
        when:
            command.handle(getExecutionContext('releases/2026/empty.groovy'))

        then:
            new File(changeLogLocation, 'releases/2026/empty.groovy').text.replaceAll(/\s/, '') == 'databaseChangeLog={}'
    }

    def "does not add the new file to the changelog of the application unless it is asked to"() {
        given:
            command.changeLogFile.text = 'databaseChangeLog = {\n}\n'

        when:
            command.handle(getExecutionContext('quiet.groovy'))

        then:
            new File(changeLogLocation, 'quiet.groovy').isFile()
            command.changeLogFile.text == 'databaseChangeLog = {\n}\n'
    }

    def "needs the name of the file"() {
        when:
            command.handle(getExecutionContext())

        then:
            def e = thrown(DatabaseMigrationException)
            e.message == 'The dbm-create-changelog command requires a filename'
    }
}
