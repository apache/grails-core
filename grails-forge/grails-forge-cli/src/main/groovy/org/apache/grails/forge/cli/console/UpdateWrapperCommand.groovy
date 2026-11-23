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
import groovy.transform.CompileStatic
import org.grails.forge.cli.command.ProjectHealthCommand
import picocli.CommandLine

/**
 * {@code update-wrapper}: replaces the Grails wrapper that is committed in the project, the files
 * {@code grailsw}, {@code grailsw.bat} and {@code grails-wrapper.jar}, by the wrapper of the version
 * of Grails this command line is of. Nothing is downloaded for it: the command line has the three
 * files with it, as it has them for an application it creates. Typed to the wrapper,
 * {@code ./grailsw update-wrapper}, the wrapper first downloads the command line of the version the
 * project names again, and that command line then replaces the wrapper it was started by.
 *
 * @since 9.0
 */
@CommandLine.Command(name = WrapperFiles.COMMAND, description = [
        'Replaces the Grails wrapper of the project by that of this version of Grails.',
        'The wrapper is the files grailsw, grailsw.bat and grails-wrapper.jar, which are committed with the project. ' +
                'Typed to the wrapper (./grailsw update-wrapper), the command line of the version the project names is downloaded again first.'
])
@CompileStatic
class UpdateWrapperCommand extends InProjectCommand {

    @Override
    protected String notDone() {
        return 'The Grails wrapper was not replaced'
    }

    @Override
    protected int run(ConsoleProject project) {
        Path directory = WrapperFiles.directoryOf(project)
        if (directory == null) {
            err("The project in ${GradleRunner.line(project.directory)} has no Grails wrapper to replace: there is no ${WrapperFiles.JAR} in it" +
                    (project.getBuildRoot() == null ? '' : " or in ${GradleRunner.line(project.getBuildRoot())}, the root of its build") +
                    ". A wrapper is the files ${WrapperFiles.NAMES.join(', ')}, which an application that create-app creates has at its root")
            return 1
        }
        WrapperFiles.Result result = new WrapperFiles(directory).update()
        String done = result.done()
        if (done != null) {
            out(done)
        }
        for (String line : result.left()) {
            err(line)
        }
        if (result.isFromEarlierMajor()) {
            // a wrapper of an earlier major version is replaced as a step of an upgrade, which has more
            out(ProjectHealthCommand.advice(spec) + '.')
        }
        return result.isSettled() ? 0 : 1
    }
}
