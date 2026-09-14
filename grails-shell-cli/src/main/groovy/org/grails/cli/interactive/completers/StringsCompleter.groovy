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
package org.grails.cli.interactive.completers

import groovy.transform.CompileStatic
import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * A completer that completes based on a collection of Strings
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class StringsCompleter implements Completer {

    private SortedSet<String> strings = new TreeSet<>()

    StringsCompleter() {
        // empty
    }

    StringsCompleter(final Collection<String> strings) {
        Objects.requireNonNull(strings)
        getStrings().addAll(strings)
    }

    StringsCompleter(final String... strings) {
        this(Arrays.asList(strings))
    }

    SortedSet<String> getStrings() {
        return strings
    }

    void setStrings(SortedSet<String> strings) {
        this.strings = strings
    }

    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        Objects.requireNonNull(candidates)

        String buffer = line.word()

        if (buffer == null || buffer.isEmpty()) {
            for (String string in getStrings()) {
                candidates.add(new Candidate(string))
            }
        } else {
            for (String match in getStrings().tailSet(buffer)) {
                if (!match.startsWith(buffer)) {
                    break
                }
                candidates.add(new Candidate(match))
            }
        }
    }

}
