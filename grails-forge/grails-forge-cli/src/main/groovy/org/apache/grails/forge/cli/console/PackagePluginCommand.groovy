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

import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * {@code package-plugin}, in a plugin project: the Gradle task {@code jar}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'package-plugin',
        description = [
                'Packages the Grails plugin into a JAR file: the Gradle task jar.',
                'What follows @|bold --|@ is given to Gradle as it is.'
        ],
        preprocessor = GradleTaskCommand.PassedOn)
@CompileStatic
class PackagePluginCommand extends GradleTaskCommand {

    @Override
    protected List<String> gradleArguments(ConsoleProject project) {
        return [project.task('jar')]
    }
}
