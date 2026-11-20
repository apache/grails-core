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
package org.grails.build.interactive;

import java.util.List;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * A Completer implementation that wraps candidate list completion behavior.
 * In JLine 3, completion handling is integrated into the LineReader itself,
 * so this class now acts as a utility completer that can be composed with others.
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class CandidateListCompletionHandler implements Completer {

    private final Completer delegate;

    public CandidateListCompletionHandler() {
        this.delegate = null;
    }

    public CandidateListCompletionHandler(Completer delegate) {
        this.delegate = delegate;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        if (delegate != null) {
            delegate.complete(reader, line, candidates);
        }
    }

    /**
     * Returns a root that matches all the {@link String} elements
     * of the specified {@link List}, or null if there are
     * no commonalities. For example, if the list contains
     * <i>foobar</i>, <i>foobaz</i>, <i>foobuz</i>, the
     * method will return <i>foob</i>.
     */
    public static String getUnambiguousCompletions(final List<Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        // convert to an array for speed
        String[] strings = new String[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            strings[i] = candidates.get(i).value();
        }

        String first = strings[0];
        StringBuilder candidate = new StringBuilder();

        for (int i = 0, count = first.length(); i < count; i++) {
            if (startsWith(first.substring(0, i + 1), strings)) {
                candidate.append(first.charAt(i));
            } else {
                break;
            }
        }

        return candidate.toString();
    }

    /**
     * @return true is all the elements of <i>candidates</i>
     *         start with <i>starts</i>
     */
    private static boolean startsWith(final String starts, final String[] candidates) {
        for (int i = 0; i < candidates.length; i++) {
            if (!candidates[i].startsWith(starts)) {
                return false;
            }
        }

        return true;
    }
}
