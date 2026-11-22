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

import groovy.transform.CompileStatic

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine
import picocli.AutoComplete
import picocli.CommandLine.Model.CommandSpec

/**
 * Completes the word under the cursor at the interactive prompt from the picocli commands, their
 * subcommands and their options.
 */
@CompileStatic
class CommandLineCompleter implements Completer {

    private final CommandSpec spec

    CommandLineCompleter(CommandSpec spec) {
        this.spec = Objects.requireNonNull(spec, 'spec')
    }

    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<CharSequence> suggestions = []
        AutoComplete.complete(spec, line.words().toArray(new String[0]), line.wordIndex(), 0, line.cursor(), suggestions)
        suggestions.each { CharSequence suggestion ->
            candidates.add(new Candidate(suggestion.toString()))
        }
    }
}
