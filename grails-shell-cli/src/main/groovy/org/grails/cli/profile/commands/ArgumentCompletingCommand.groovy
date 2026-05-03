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

package org.grails.cli.profile.commands

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

import org.grails.build.parsing.CommandLine
import org.grails.build.parsing.CommandLineParser
import org.grails.cli.profile.Command
import org.grails.cli.profile.CommandDescription

/**
 * @author graemerocher
 */
abstract class ArgumentCompletingCommand implements Command, Completer {

    CommandLineParser cliParser = new CommandLineParser()

    @Override
    final void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        def desc = getDescription()
        def commandLine = cliParser.parseString(line.line())
        complete(commandLine, desc, candidates)
    }

    protected void complete(CommandLine commandLine, CommandDescription desc, List<Candidate> candidates) {
        def invalidOptions = commandLine.undeclaredOptions.keySet().findAll { String str ->
            desc.getFlag(str.trim()) == null
        }

        def lastOption = commandLine.lastOption()

        for (arg in desc.flags) {
            def argName = arg.name
            def flag = "-$argName".toString()
            if (!commandLine.hasOption(arg.name)) {
                if (lastOption) {
                    def lastArg = lastOption.key
                    if (arg.name.startsWith(lastArg)) {
                        candidates.add(new Candidate("$flag ".toString()))
                    } else if (!invalidOptions) {
                        candidates.add(new Candidate("$flag ".toString()))
                    }
                } else {
                    candidates.add(new Candidate("$flag ".toString()))
                }
            }
        }
    }
}
