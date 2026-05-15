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

import java.util.regex.Pattern

import org.jline.reader.Candidate
import org.jline.reader.Completer
import org.jline.reader.LineReader
import org.jline.reader.ParsedLine

/**
 * JLine Completor that accepts a string if it matches a given regular
 * expression pattern.
 *
 * @author Peter Ledbrook
 * @since 2.0
 */
class RegexCompletor implements Completer {

    Pattern pattern

    RegexCompletor(String pattern) {
        this(Pattern.compile(pattern))
    }

    RegexCompletor(Pattern pattern) {
        this.pattern = pattern
    }

    /**
     * <p>Check whether the whole buffer matches the configured pattern.
     * If it does, the buffer is added to the <tt>candidates</tt> list
     * (which indicates acceptance of the buffer string).
     * </p>
     * <p>If the buffer doesn't match the configured pattern, the
     * <tt>candidates</tt> list is left empty.</p>
     */
    @Override
    void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        String buffer = line.word()
        if (buffer ==~ pattern) {
            candidates.add(new Candidate(buffer))
        }
    }
}
