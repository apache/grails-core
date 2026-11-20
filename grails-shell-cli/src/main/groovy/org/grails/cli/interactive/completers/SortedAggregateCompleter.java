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
package org.grails.cli.interactive.completers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

/**
 * An aggregate completer that sorts completion candidates.
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class SortedAggregateCompleter implements Completer {
    
    private final List<Completer> completers = new ArrayList<>();

    public SortedAggregateCompleter() {
        // empty
    }

    /**
     * Construct an AggregateCompleter with the given collection of completers.
     * The completers will be used in the iteration order of the collection.
     *
     * @param completers the collection of completers
     */
    public SortedAggregateCompleter(final Collection<Completer> completers) {
        Objects.requireNonNull(completers);
        this.completers.addAll(completers);
    }

    /**
     * Construct an AggregateCompleter with the given completers.
     * The completers will be used in the order given.
     *
     * @param completers the completers
     */
    public SortedAggregateCompleter(final Completer... completers) {
        this(Arrays.asList(completers));
    }

    /**
     * Retrieve the collection of completers currently being aggregated.
     *
     * @return the aggregated completers
     */
    public Collection<Completer> getCompleters() {
        return completers;
    }

    /**
     * Perform a completion operation across all aggregated completers.
     */
    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
        Objects.requireNonNull(candidates);

        List<Candidate> allCandidates = new ArrayList<>();

        // Run each completer, collecting candidates
        for (Completer completer : completers) {
            List<Candidate> completerCandidates = new ArrayList<>();
            completer.complete(reader, line, completerCandidates);
            allCandidates.addAll(completerCandidates);
        }

        // Sort the candidates by their value
        allCandidates.sort(Comparator.comparing(Candidate::value));

        candidates.addAll(allCandidates);
    }

    /**
     * @return a string representing the aggregated completers
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
            "completers=" + completers +
            '}';
    }
}
