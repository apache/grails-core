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
import org.apache.grails.forge.cli.console.ToolCatalogFile

/**
 * {@code tool-catalog}: the catalog of the tools of the application and its plugins that the build
 * writes to {@code .grails/cache/tool-catalog.json}, which is how the {@code grails} command has
 * them. Nothing here is more than advice: the build of the project lists them. A catalog that is
 * current and says that the build could not list everything, such as when the class path the tools
 * run with does not resolve, is named with the first thing the build says.
 *
 * @since 9.0
 */
@CompileStatic
final class ToolCatalogCheck extends HealthCheck {

    @Override
    String getName() {
        return 'tool-catalog'
    }

    @Override
    List<HealthFinding> check(HealthContext context) {
        ToolCatalogFile file = ToolCatalogFile.read(context.directory)
        String task = context.toType("gradle ${ToolCatalogFile.TASK}")
        String fix = "A build that compiles the project lists them (grails compile, run-app, test-app, assemble), and so does typing the name of one; ${task} lists them now"
        List<String> files = [ToolCatalogFile.LOCATION]
        switch (file.state) {
            case ToolCatalogFile.State.MISSING:
                return [found('missing', HealthLevel.ADVICE, "The tools of the application and its plugins are not listed: there is no ${ToolCatalogFile.LOCATION}, " +
                        'so the grails command does not have them by their names', fix, task, files, NOTE_COMMAND_CATALOG)]
            case ToolCatalogFile.State.REFUSED:
                return [found('refused', HealthLevel.ADVICE, "${ToolCatalogFile.LOCATION} ${file.reason}, so it is not used, and the grails command does not have " +
                        'the tools of the application and its plugins by their names', fix, task, files, NOTE_COMMAND_CATALOG)]
            case ToolCatalogFile.State.STALE:
                return [found('stale', HealthLevel.ADVICE, "${ToolCatalogFile.LOCATION} may not list the tools the build has now: ${file.reason} was changed " +
                        'after it was written', fix, task, files, NOTE_COMMAND_CATALOG)]
        }
        String listed = "${file.tools.size()} ${file.tools.size() == 1 ? 'tool' : 'tools'}"
        // what the build could not list, as it says it, and what of the file is not used
        List<String> said = file.diagnostics + file.leftOut
        if (!file.complete || !file.leftOut.isEmpty()) {
            String first = said ? said.first() : 'the build does not say why'
            String more = said.size() > 1 ? " (and ${said.size() - 1} more)" : ''
            return [found('incomplete', HealthLevel.ADVICE, "${ToolCatalogFile.LOCATION} lists ${listed} of the application and its plugins, and not every one " +
                    "the build has: ${first}${more}", "${context.toType('help')} lists what is not listed and why. A build that compiles the project lists " +
                    'the tools again once that is corrected', context.toType('help'), files, NOTE_COMMAND_CATALOG)]
        }
        return [ok("The tools of the application and its plugins are listed (${ToolCatalogFile.LOCATION}): ${listed}", files)]
    }
}
