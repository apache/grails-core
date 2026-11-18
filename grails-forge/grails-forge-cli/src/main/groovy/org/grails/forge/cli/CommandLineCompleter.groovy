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

import java.util.function.Supplier

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
 *
 * <p>The commands can be given as a supplier, so the prompt completes the commands offered now, such as
 * those of a project whose build changed since the prompt started.</p>
 */
@CompileStatic
class CommandLineCompleter implements Completer {

    private final Supplier<CommandSpec> specs

    CommandLineCompleter(CommandSpec spec) {
        Objects.requireNonNull(spec, 'spec')
        this.specs = { -> spec } as Supplier<CommandSpec>
    }

    CommandLineCompleter(Supplier<CommandSpec> specs) {
        this.specs = Objects.requireNonNull(specs, 'specs')
    }

    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<String> words = new ArrayList<>(line.words())
        int wordIndex = line.wordIndex()
        String word = wordIndex < words.size() ? words[wordIndex] : ''
        int separator = word.indexOf('=')
        // picocli completes the value of an option only as a word of its own, so --name=value is completed
        // as --name value and the values are offered with the option in front of them
        String prefix = ''
        if (word.startsWith('--') && separator > 0) {
            prefix = word.substring(0, separator + 1)
            words.set(wordIndex, word.substring(0, separator))
            words.add(wordIndex + 1, word.substring(separator + 1))
            wordIndex++
        }
        List<CharSequence> suggestions = []
        AutoComplete.complete(specs.get(), words.toArray(new String[0]), wordIndex, 0, line.cursor(), suggestions)
        suggestions.each { CharSequence suggestion ->
            candidates.add(new Candidate(prefix + suggestion))
        }
    }
}
