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
package org.apache.grails.forge.cli.console

import java.nio.file.Path
import java.util.concurrent.Callable
import groovy.transform.CompileStatic
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.io.FileSystemOutputHandler

/**
 * A command that works on the Grails project the current directory is in, and does nothing outside one.
 *
 * @since 9.0
 */
@CompileStatic
abstract class InProjectCommand extends BaseCommand implements Callable<Integer>, ProjectCommand {

    @Override
    Integer call() throws Exception {
        Path current = FileSystemOutputHandler.getDefaultBaseDirectory().toPath()
        ConsoleProject.Search search = ConsoleProject.search(current)
        ConsoleProject project = search.project
        if (project == null) {
            err(search.none(UsageHelp.typed(spec)))
            return 1
        }
        return run(project)
    }

    /**
     * @return what did not happen, for the lines of the command that say why it did nothing, as a sentence without the full stop
     */
    protected abstract String notDone()

    /**
     * @param project the project the current directory is in
     * @return the exit code of the command
     */
    protected abstract int run(ConsoleProject project)
}
