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
package org.apache.grails.datamapping.core.cli.querytail

import groovy.transform.CompileStatic

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ExecutionContext
import org.apache.grails.core.cli.StandaloneCommand
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.build.parsing.CommandLine
import org.grails.config.CodeGenConfig

/**
 * The {@code query-tail} command: shows the statements the running application executes, by the page
 * that runs them. It follows the capture file the application writes and is run without the
 * application (see {@link StandaloneCommand}): a second instance of the application, with its data
 * source and its {@code BootStrap}, is not what reads a file.
 *
 * @since 9.0
 */
@CompileStatic
class QueryTailCommand implements ApplicationCommand, StandaloneCommand {

    /** The options of the command, each given with two dashes. */
    public static final List<String> FLAGS = ['statements', 'stream', 'page', 'top', 'all', 'once', 'file'].asImmutable()

    /** What {@code --help} prints. */
    public static final List<String> USAGE = [
            'Usage: grails query-tail [--statements | --stream] [--page=<controller/action>] [--top=<n>] [--all] [--once] [--file=<path>]',
            '',
            '  --statements  Rank the statements themselves instead of the pages that run them',
            '  --stream      Print every statement as it runs, grouped by request, instead of a summary',
            '  --page        Only the statements of one page, as controller/action or a controller name',
            "  --top         How many rows to show (default ${QueryTailOptions.DEFAULT_TOP})".toString(),
            '  --all         Read what the application has run so far, not only what it runs from now on',
            '  --once        Print the summary of what has been captured and exit',
            '  --file        The capture file, a relative one read against the application directory; by default the one',
            '                the application writes under the build directory',
    ].asImmutable()

    final String description = 'Shows the statements the running application executes, SQL, Cypher or MongoDB commands, by the page that runs them'

    @Override
    boolean handle(ExecutionContext executionContext) {
        CommandLine commandLine = executionContext.commandLine
        PrintStream out = System.out
        if (commandLine.hasOption('help')) {
            USAGE.each { String line -> out.println(line) }
            return true
        }
        File baseDir = executionContext.baseDir
        QueryTailOptions options
        try {
            Map<String, Object> flags = [:]
            for (String flag : FLAGS) {
                flags.put(flag, commandLine.optionValue(flag))
            }
            options = QueryTailOptions.from(flags, defaultCaptureFile(baseDir, executionContext.targetDir), baseDir)
        }
        catch (IllegalArgumentException e) {
            System.err.println("| Error ${e.message}")
            return false
        }

        // The build that runs the command is no terminal: the summary is plain text of the default width.
        QueryTailRunner runner = new QueryTailRunner(options, out, false)
        // The run ends when the command is cancelled, which is the end of the JVM the build runs it in. The hook is
        // there while the command runs: a JVM that runs the command again keeps no hook of a run that has ended.
        Thread stopping = new Thread({ -> runner.stop() } as Runnable, 'query-tail-stop')
        Runtime.runtime.addShutdownHook(stopping)
        try {
            return runner.run() == 0
        } finally {
            try {
                Runtime.runtime.removeShutdownHook(stopping)
            } catch (IllegalStateException ignored) {
                // the JVM is ending, and the hook stops the run
            }
        }
    }

    /**
     * The file the application writes, as the application finds it: the one {@code grails.gorm.queryTail.file}
     * names, as a system property, which is how the build hands the command a {@code -Pgrails.gorm.queryTail.file}
     * it was given, else in {@code grails-app/conf/application.yml} or {@code application.groovy}; else
     * {@code query-tail/capture.log} under the build directory. The files are read as they are written:
     * the application is not started to ask it.
     *
     * @param baseDir the directory of the application
     * @param buildDir its build directory
     * @return the capture file
     */
    static File defaultCaptureFile(File baseDir, File buildDir) {
        String given = System.getProperty(QueryTailSettings.SETTING_FILE)
        if (given) {
            return QueryTailSettings.captureFile(given, baseDir, buildDir)
        }
        CodeGenConfig config = new CodeGenConfig()
        File yml = new File(baseDir, 'grails-app/conf/application.yml')
        File groovy = new File(baseDir, 'grails-app/conf/application.groovy')
        if (yml.isFile()) {
            config.loadYml(yml)
        }
        if (groovy.isFile()) {
            config.loadGroovy(groovy)
        }
        Object configured = config.navigate('grails', 'gorm', 'queryTail', 'file')
        QueryTailSettings.captureFile(configured ? configured.toString() : null, baseDir, buildDir)
    }
}
