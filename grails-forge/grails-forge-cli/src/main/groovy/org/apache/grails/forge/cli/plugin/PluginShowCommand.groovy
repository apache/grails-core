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
package org.apache.grails.forge.cli.plugin

import groovy.transform.CompileStatic
import org.grails.forge.cli.ForgeContext
import picocli.CommandLine

/**
 * {@code plugin show}, and by its other name {@code plugin info}: {@code plugin-info} by its name
 * among the commands of {@code plugin}. It is that command, with its options and what it writes; only
 * the commands it names are those of {@code plugin}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = PluginShowCommand.NAME, aliases = [PluginShowCommand.OTHER_NAME], description = [
        'Says what is known of one Grails plugin: its versions, what it is for, its license, where its documentation, issues and sources are,',
        'and the line that adds it to the build of an application. @|bold plugin list|@ lists the plugins.',
        'The command is also there as @|bold plugin info|@ and as @|bold plugin-info|@.'])
@CompileStatic
class PluginShowCommand extends PluginInfoCommand {

    public static final String NAME = 'show'

    /** The other name of the command */
    public static final String OTHER_NAME = 'info'

    /**
     * @param context the Forge application of the command line, see {@link PluginsCommand#PluginsCommand(ForgeContext)}
     */
    PluginShowCommand(ForgeContext context) {
        super(context)
    }

    @Override
    protected String listCommand() {
        return "${PluginGroupCommand.NAME} ${PluginListCommand.NAME}"
    }
}
