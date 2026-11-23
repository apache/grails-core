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

import java.util.concurrent.Callable
import groovy.transform.CompileStatic
import org.grails.forge.cli.command.BaseCommand
import org.grails.forge.cli.command.GradleCommand
import picocli.CommandLine

/**
 * {@code install}, which the plugin profiles had for the Gradle task of that name. A plugin is put
 * into the local Maven repository by {@code publishToMavenLocal} now, so the command runs nothing and
 * says what to run.
 *
 * @since 9.0
 */
@CommandLine.Command(name = 'install', hidden = true,
        description = 'Says how a plugin is installed into the local Maven repository: with the Gradle task publishToMavenLocal')
@CompileStatic
class InstallCommand extends BaseCommand implements Callable<Integer> {

    @CommandLine.Unmatched
    List<String> ignored = []

    @Override
    Integer call() throws Exception {
        err('There is no install command any more, and nothing was installed: a plugin is put into the local Maven repository by Gradle. ' +
                "Run: ${UsageHelp.toType(spec, "${GradleCommand.NAME} publishToMavenLocal")}")
        return 1
    }
}
