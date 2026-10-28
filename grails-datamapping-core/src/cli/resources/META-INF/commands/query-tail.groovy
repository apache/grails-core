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

import org.grails.cli.profile.CommandCancellationListener
import org.apache.grails.datamapping.core.cli.querytail.QueryTailOptions
import org.apache.grails.datamapping.core.cli.querytail.QueryTailRenderer
import org.apache.grails.datamapping.core.cli.querytail.QueryTailRunner
import org.apache.grails.datastore.core.querytail.QueryTailSettings

description('Shows the statements the running application executes, SQL, Cypher or MongoDB commands, by the page that runs them') {
    usage 'grails query-tail [--statements | --stream] [--page=<controller/action>] [--top=<n>] [--all] [--once] [--file=<path>]'
    flag name: 'statements', description: 'Rank the statements themselves instead of the pages that run them'
    flag name: 'stream', description: 'Print every statement as it runs, grouped by request, instead of a summary'
    flag name: 'page', description: 'Only the statements of one page, as controller/action or a controller name'
    flag name: 'top', description: "How many rows to show (default ${QueryTailOptions.DEFAULT_TOP})"
    flag name: 'all', description: 'Read what the application has run so far, not only what it runs from now on'
    flag name: 'once', description: 'Print the summary of what has been captured and exit'
    flag name: 'file', description: 'The capture file, a relative one read against the application directory; by default the one the application writes under the build directory'
}

QueryTailOptions options
try {
    options = QueryTailOptions.from(
            [statements: flag('statements'), stream: flag('stream'), page: flag('page'), top: flag('top'),
             all: flag('all'), once: flag('once'), file: flag('file')],
            defaultCaptureFile(), baseDir)
}
catch (IllegalArgumentException e) {
    grailsConsole.error(e.message)
    return false
}
options.width = consoleWidth()

QueryTailRunner runner = new QueryTailRunner(options, grailsConsole.out, grailsConsole.ansiEnabled)
addCancelledListener({ -> runner.stop() } as CommandCancellationListener)
return runner.run() == 0

/**
 * The file the application writes, as the application finds it: the one named by grails.gorm.queryTail.file,
 * else query-tail/capture.log under the build directory.
 */
File defaultCaptureFile() {
    QueryTailSettings.captureFile(navigateConfig('grails', 'gorm', 'queryTail', 'file') as String, baseDir, buildDir)
}

/**
 * The width of the terminal the summary is shown on; output that is not a terminal gets the default.
 */
int consoleWidth() {
    try {
        int width = grailsConsole.ansiEnabled ? (grailsConsole.terminal?.width ?: 0) : 0
        return width > 20 ? width : QueryTailRenderer.DEFAULT_WIDTH
    }
    catch (Throwable ignored) {
        return QueryTailRenderer.DEFAULT_WIDTH
    }
}
