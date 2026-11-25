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
package org.grails.forge.cli.command

import groovy.transform.CompileStatic
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component
import picocli.CommandLine
import java.util.concurrent.Callable

/**
 * The commands for the features of the application in the current directory.
 *
 * @since 9.0
 */
@CommandLine.Command(name = FeatureCommand.NAME, description = 'Works with the features of the application in the current directory',
        subcommands = [FeatureAddCommand, FeatureListCommand, FeatureSearchCommand, FeatureInfoCommand, FeatureDiffCommand])
@Component
@Scope('prototype')
@CompileStatic
class FeatureCommand extends BaseCommand implements Callable<Integer> {

    public static final String NAME = 'feature'

    @Override
    Integer call() throws Exception {
        throw new CommandLine.ParameterException(spec.commandLine(), 'No command specified: feature add adds features to the application, and feature list lists them; feature search lists the features ' +
                'that have a text in their name or their description, feature info says what is known of one, and feature diff shows what feature add would change')
    }
}
