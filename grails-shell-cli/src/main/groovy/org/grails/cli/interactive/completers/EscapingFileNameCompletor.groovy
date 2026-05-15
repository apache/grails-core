/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.cli.interactive.completers

import org.jline.builtins.Completers.FileNameCompleter
import org.jline.reader.Candidate
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * JLine Completor that does file path matching like FileNameCompleter,
 * but in addition it escapes whitespace in completions with the '\'
 * character.
 *
 * @author Peter Ledbrook
 * @since 2.0
 */
class EscapingFileNameCompletor extends FileNameCompleter {

    /**
     * <p>Gets FileNameCompleter to create a list of candidates and then
     * inserts '\' before any whitespace characters in each of the candidates.
     * If a candidate ends in a whitespace character, then that is <em>not</em>
     * escaped.</p>
     */
    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        List<Candidate> tempCandidates = []
        super.complete(reader, line, tempCandidates)

        for (Candidate candidate : tempCandidates) {
            String value = candidate.value()
            // Escape whitespace in the value, except for trailing whitespace
            String escapedValue = value.replaceAll(/(\s)(?!$)/, '\\\\$1')
            candidates.add(new Candidate(escapedValue, candidate.displ(), candidate.group(), 
                    candidate.descr(), candidate.suffix(), candidate.key(), candidate.complete()))
        }
    }
}
