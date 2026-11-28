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
package org.grails.forge.cli

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import groovy.transform.CompileStatic
import org.grails.forge.cli.command.CodeGenCommand
import picocli.AutoComplete
import picocli.CommandLine

/**
 * Writes the completion script of the command line for bash and zsh, which the distribution has in its
 * {@code bin} directory: the build runs it, with the name the script completes and the file to write.
 *
 * <p>The script completes the commands of the command line itself as the program has them, those of a
 * project among them, with their options, and the values the command line knows without a project:
 * the features of Grails for {@code --features} and {@code feature add}. The commands of an
 * application and its plugins are not in it: they are those of one project, and the interactive
 * console completes them there.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class CompletionScript {

    private CompletionScript() {
    }

    /**
     * @param args the name the script completes, and the file to write it to
     */
    static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException('Give the name the script completes and the file to write it to')
        }
        Path file = Path.of(args[1])
        Files.createDirectories(file.toAbsolutePath().getParent())
        Files.writeString(file, bash(args[0]), StandardCharsets.UTF_8)
        // the generator, which lists the features, was started for it and keeps threads of its own
        System.exit(0)
    }

    /**
     * @param name the name the script completes
     * @return the script
     */
    static String bash(String name) {
        // what is read of the current directory is read of one that is no project, whatever directory this runs in
        Path nowhere = Files.createTempDirectory('grails-completion')
        String previous = System.getProperty('user.dir')
        System.setProperty('user.dir', nowhere.toString())
        ForgeContext context = new ForgeContext()
        try {
            CommandLine commandLine = ForgeCli.createCommandLine(context, true)
            commandLine.setCommandName(name)
            // the commands of a project are hidden from the help outside one, and are commands in every project
            for (CommandLine each : commandLine.getSubcommands().values()) {
                if (each.getCommandSpec().userObject() instanceof CodeGenCommand) {
                    each.getCommandSpec().usageMessage().hidden(false)
                }
            }
            return AutoComplete.bash(name, commandLine)
        } finally {
            context.close()
            System.setProperty('user.dir', previous)
            Files.deleteIfExists(nowhere)
        }
    }
}
