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

import liquibase.command.CommandResultsBuilder
import liquibase.command.CommandScope
import liquibase.command.core.GenerateChangelogCommandStep
import liquibase.command.core.DiffCommandStep
import liquibase.command.core.helpers.DiffOutputControlCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.ObjectQuotingStrategy
import liquibase.diff.DiffResult
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.serializer.ChangeLogSerializer
import spock.lang.Specification

class GroovyGenerateChangeLogCommandStepSpec extends Specification {

    GroovyGenerateChangeLogCommandStep step = new GroovyGenerateChangeLogCommandStep()
    CommandResultsBuilder resultsBuilder = Mock()
    CommandScope commandScope = Mock()
    Database database = Mock()
    DiffOutputControl diffOutputControl = Mock()
    DiffResult diffResult = Mock()
    DiffToChangeLog diffToChangeLog = Mock()
    DiffCommandStep diffCommandStep = Mock()

    def 'defineCommandNames returns correct name'() {
        expect:
        step.defineCommandNames() == [['groovyGenerateChangeLog'] as String[]] as String[][]
    }

    def 'run executes generateChangelog and prints groovy changelog'() {
        given:
        resultsBuilder.getCommandScope() >> commandScope
        commandScope.getArgumentValue(GenerateChangelogCommandStep.CHANGELOG_FILE_ARG) >> null
        commandScope.getArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG) >> database
        commandScope.getArgumentValue(GenerateChangelogCommandStep.AUTHOR_ARG) >> 'author'
        commandScope.getArgumentValue(GenerateChangelogCommandStep.CONTEXT_ARG) >> 'context'
        
        resultsBuilder.getResult(DiffOutputControlCommandStep.DIFF_OUTPUT_CONTROL.getName()) >> diffOutputControl
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream()
        resultsBuilder.getOutputStream() >> baos

        database.getObjectQuotingStrategy() >> ObjectQuotingStrategy.LEGACY

        GroovyGenerateChangeLogCommandStep stepSpy = Spy(GroovyGenerateChangeLogCommandStep)
        stepSpy.createDiffCommandStep() >> diffCommandStep
        stepSpy.createDiffToChangeLogObject(diffResult, diffOutputControl) >> diffToChangeLog

        when:
        stepSpy.run(resultsBuilder)

        then:
        1 * diffCommandStep.createDiffResult(resultsBuilder) >> diffResult
        
        1 * diffToChangeLog.setChangeSetAuthor('author')
        1 * diffToChangeLog.setChangeSetContext('context')
        1 * diffToChangeLog.setChangeSetPath(null)

        1 * database.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS)
        
        1 * diffToChangeLog.print(_ as PrintStream, _ as ChangeLogSerializer)
        
        1 * database.setObjectQuotingStrategy(ObjectQuotingStrategy.LEGACY)
    }

    def 'run executes generateChangelog and prints to file if specified'() {
        given:
        resultsBuilder.getCommandScope() >> commandScope
        commandScope.getArgumentValue(GenerateChangelogCommandStep.CHANGELOG_FILE_ARG) >> 'changelog.groovy'
        commandScope.getArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG) >> database
        
        resultsBuilder.getResult(DiffOutputControlCommandStep.DIFF_OUTPUT_CONTROL.getName()) >> diffOutputControl
        
        database.getObjectQuotingStrategy() >> ObjectQuotingStrategy.LEGACY

        GroovyGenerateChangeLogCommandStep stepSpy = Spy(GroovyGenerateChangeLogCommandStep)
        stepSpy.createDiffCommandStep() >> diffCommandStep
        stepSpy.createDiffToChangeLogObject(diffResult, diffOutputControl) >> diffToChangeLog

        when:
        stepSpy.run(resultsBuilder)

        then:
        1 * diffCommandStep.createDiffResult(resultsBuilder) >> diffResult
        
        1 * diffToChangeLog.print('changelog.groovy', _ as ChangeLogSerializer)
    }
}
