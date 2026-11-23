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
package org.apache.grails.forge.cli.health

import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ApplicationCommandFile

/**
 * {@code command-catalog}: the list of the commands of the application and its plugins that the
 * build writes to {@code .grails/cache/command-catalog.json}, which is how the {@code grails}
 * command has them. Nothing here is more than advice: the build of the project lists them.
 *
 * @since 9.0
 */
@CompileStatic
final class CommandCatalogCheck extends HealthCheck {

    @Override
    String getName() {
        return 'command-catalog'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        ApplicationCommandFile file = ApplicationCommandFile.read(context.directory)
        String task = context.toType("gradle ${ApplicationCommandFile.TASK}")
        String fix = "Any build of the project lists them, or ${task} lists them now"
        List<String> files = [ApplicationCommandFile.LOCATION]
        switch (file.state) {
            case ApplicationCommandFile.State.MISSING:
                return [found('missing', HealthLevel.ADVICE, "The commands of the application and its plugins are not listed: there is no ${ApplicationCommandFile.LOCATION}, " +
                        'so the grails command does not have them by their names', fix, task, files, NOTE_COMMAND_CATALOG)]
            case ApplicationCommandFile.State.REFUSED:
                return [found('refused', HealthLevel.ADVICE, "${ApplicationCommandFile.LOCATION} ${file.reason}, so it is not used, and the grails command does not have " +
                        'the commands of the application and its plugins by their names', fix, task, files, NOTE_COMMAND_CATALOG)]
            case ApplicationCommandFile.State.STALE:
                return [found('stale', HealthLevel.ADVICE, "${ApplicationCommandFile.LOCATION} may not list the commands the build has now: ${file.reason} was changed " +
                        'after it was written', fix, task, files, NOTE_COMMAND_CATALOG)]
            default:
                return [ok("The commands of the application and its plugins are listed (${ApplicationCommandFile.LOCATION}): ${file.commands.size()} " +
                        "${file.commands.size() == 1 ? 'command' : 'commands'}", files)]
        }
    }
}
