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
 * {@code plugin list}: {@code list-plugins} by its name among the commands of {@code plugin}. It is
 * that command, with its options and what it writes; only the commands it names are those of
 * {@code plugin}.
 *
 * @since 9.0
 */
@CommandLine.Command(name = PluginListCommand.NAME, description = [
        'Lists the Grails plugins: those of this release of Grails, and those of the plugin index of Grails, which is read from the network.',
        'Each has its name, its coordinates, the latest version for this Grails and what it is for; @|bold plugin show|@ says more of one.',
        'The command is also there as @|bold list-plugins|@.'])
@CompileStatic
class PluginListCommand extends ListPluginsCommand {

    public static final String NAME = 'list'

    /**
     * @param context the Forge application of the command line, see {@link PluginsCommand#PluginsCommand(ForgeContext)}
     */
    PluginListCommand(ForgeContext context) {
        super(context)
    }

    @Override
    protected String showCommand() {
        return "${PluginGroupCommand.NAME} ${PluginShowCommand.NAME}"
    }
}
