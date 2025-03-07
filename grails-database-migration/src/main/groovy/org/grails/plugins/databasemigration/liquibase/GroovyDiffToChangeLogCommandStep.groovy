/*
 * Copyright 2015 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.command.CommandResultsBuilder
import liquibase.command.CommandScope
import liquibase.command.core.DiffChangelogCommandStep
import liquibase.command.core.DiffCommandStep
import liquibase.command.core.InternalSnapshotCommandStep
import liquibase.command.core.helpers.DiffOutputControlCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.ObjectQuotingStrategy
import liquibase.diff.DiffResult
import liquibase.diff.output.DiffOutputControl
import liquibase.serializer.ChangeLogSerializerFactory
import grails.util.GrailsStringUtils

@CompileStatic
class GroovyDiffToChangeLogCommandStep extends DiffChangelogCommandStep {

    public static final String[] COMMAND_NAME = new String[] {"groovyDiffChangelog"}

    @Override
    void run(CommandResultsBuilder resultsBuilder) {
        CommandScope commandScope = resultsBuilder.getCommandScope()
        Database referenceDatabase = commandScope.getArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG);
        String changeLogFile = commandScope.getArgumentValue(CHANGELOG_FILE_ARG);

        InternalSnapshotCommandStep.logUnsupportedDatabase(referenceDatabase, this.getClass());

        DiffCommandStep diffCommandStep = new DiffCommandStep()

        DiffResult diffResult = diffCommandStep.createDiffResult(resultsBuilder);

        PrintStream outputStream = new PrintStream(resultsBuilder.getOutputStream());

        ObjectQuotingStrategy originalStrategy = referenceDatabase.getObjectQuotingStrategy();

        DiffOutputControl diffOutputControl = (DiffOutputControl) resultsBuilder.getResult(DiffOutputControlCommandStep.DIFF_OUTPUT_CONTROL.getName())

        try {
            referenceDatabase.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
            if (GrailsStringUtils.trimToNull(changeLogFile) == null) {
                createDiffToChangeLogObject(diffResult, diffOutputControl, false).print(outputStream, ChangeLogSerializerFactory.instance.getSerializer('groovy'))
            } else {
                createDiffToChangeLogObject(diffResult, diffOutputControl, false).print(changeLogFile, ChangeLogSerializerFactory.instance.getSerializer(changeLogFile))
            }
        }
        finally {
            referenceDatabase.setObjectQuotingStrategy(originalStrategy);
            outputStream.flush();
        }
        resultsBuilder.addResult("statusCode", 0);

    }

    @Override
    String[][] defineCommandNames() {
        return new String[][] { COMMAND_NAME }
    }

}
