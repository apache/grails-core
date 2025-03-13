/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.plugins.databasemigration.command

import grails.dev.commands.ApplicationCommand
import spock.lang.AutoCleanup

class DbmStatusCommandSpec extends ApplicationContextDatabaseMigrationCommandSpec {

    @Override
    protected Class<ApplicationCommand> getCommandClass() {
        return DbmStatusCommand
    }

    @AutoCleanup('delete')
    File outputFile = File.createTempFile('update', 'sql')

    def "outputs count or list of unrun change sets to STDOUT"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext())

        then:
            output.toString().contains('2 changesets have not been applied')
    }

    def "outputs count or list of unrun change sets to a file given as arguments"() {
        given:
            command.changeLogFile << CHANGE_LOG_CONTENT

        when:
            command.handle(getExecutionContext(outputFile.canonicalPath))

        then:
            outputFile.text.contains('2 changesets have not been applied')
    }

    static final String CHANGE_LOG_CONTENT = '''
databaseChangeLog = {
    changeSet(author: "John Smith", id: "changeSet1") {
    }
    changeSet(author: "John Smith", id: "changeSet2") {
    }
}
'''
}
