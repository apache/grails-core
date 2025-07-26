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

import groovy.transform.CompileStatic
import liquibase.Scope
import liquibase.command.CommandResultsBuilder
import liquibase.command.CommandScope
import liquibase.command.core.DiffCommandStep
import liquibase.command.core.GenerateChangelogCommandStep
import liquibase.command.core.InternalSnapshotCommandStep
import liquibase.command.core.helpers.DiffOutputControlCommandStep
import liquibase.command.core.helpers.ReferenceDbUrlConnectionCommandStep
import liquibase.database.Database
import liquibase.database.ObjectQuotingStrategy
import liquibase.diff.DiffResult
import liquibase.diff.output.DiffOutputControl
import liquibase.diff.output.changelog.DiffToChangeLog
import liquibase.serializer.ChangeLogSerializerFactory
import grails.util.GrailsStringUtils

@CompileStatic
class GroovyGenerateChangeLogCommandStep extends GenerateChangelogCommandStep {

    public static final String[] COMMAND_NAME = new String[] {"groovyGenerateChangeLog"}

    private static final String INFO_MESSAGE =
            "When generating formatted SQL changelogs, it is important to decide if batched statements\n" +
                    "should be split or not.  For storedlogic objects, the default behavior is 'splitStatements:false'\n." +
                    "All other objects default to 'splitStatements:true'.  See https://docs.liquibase.org for additional information."

    @Override
    void run(CommandResultsBuilder resultsBuilder) throws Exception {
        CommandScope commandScope = resultsBuilder.getCommandScope()

        String changeLogFile = GrailsStringUtils.trimToNull(commandScope.getArgumentValue(CHANGELOG_FILE_ARG))
        if (changeLogFile != null && changeLogFile.toLowerCase().endsWith(".sql")) {
            Scope.getCurrentScope().getUI().sendMessage("\n" + INFO_MESSAGE + "\n")
            Scope.getCurrentScope().getLog(getClass()).info("\n" + INFO_MESSAGE + "\n")
        }

        final Database referenceDatabase = commandScope.getArgumentValue(ReferenceDbUrlConnectionCommandStep.REFERENCE_DATABASE_ARG)

        InternalSnapshotCommandStep.logUnsupportedDatabase(referenceDatabase, this.getClass())

        DiffCommandStep diffCommandStep = new DiffCommandStep()

        DiffResult diffResult = diffCommandStep.createDiffResult(resultsBuilder)

        DiffOutputControl diffOutputControl = (DiffOutputControl) resultsBuilder.getResult(DiffOutputControlCommandStep.DIFF_OUTPUT_CONTROL.getName())

        DiffToChangeLog changeLogWriter = new DiffToChangeLog(diffResult, diffOutputControl)

        changeLogWriter.setChangeSetAuthor(commandScope.getArgumentValue(AUTHOR_ARG))
        changeLogWriter.setChangeSetContext(commandScope.getArgumentValue(CONTEXT_ARG))
        changeLogWriter.setChangeSetPath(changeLogFile)

        ObjectQuotingStrategy originalStrategy = referenceDatabase.getObjectQuotingStrategy()
        try {
            referenceDatabase.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS)
            if (GrailsStringUtils.trimToNull(changeLogFile) != null) {
                changeLogWriter.print(changeLogFile, ChangeLogSerializerFactory.instance.getSerializer(changeLogFile))
            } else {
                PrintStream outputStream = new PrintStream(resultsBuilder.getOutputStream())
                try {
                    changeLogWriter.print(outputStream, ChangeLogSerializerFactory.instance.getSerializer('groovy'))
                } finally {
                    outputStream.flush()
                }

            }
            if (GrailsStringUtils.trimToNull(changeLogFile) != null) {
                Scope.getCurrentScope().getUI().sendMessage("Generated changelog written to " + new File(changeLogFile).getAbsolutePath())
            }
        } finally {
            referenceDatabase.setObjectQuotingStrategy(originalStrategy)
        }
    }

    @Override
    String[][] defineCommandNames() {
        return new String[][] { COMMAND_NAME }
    }
}
