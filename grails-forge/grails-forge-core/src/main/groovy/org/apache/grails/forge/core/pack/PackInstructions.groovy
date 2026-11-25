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
package org.apache.grails.forge.core.pack

import groovy.transform.CompileStatic
import java.util.regex.Pattern
import org.grails.forge.feature.Feature

/**
 * What the descriptor of a feature pack says is to be done once the application has the feature: its
 * {@code instructions}, as a command shows them after it created the application or added the feature.
 *
 * <p>The text is that of a file nobody has checked, so it is shown as text and nothing else: every
 * line is cut at {@link #MAX_LINE} characters, at most {@link #MAX_LINES} lines are shown, and no
 * character that acts on a terminal is among them. Nothing of it is run or read as markup.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class PackInstructions {

    /** The most lines of the instructions of one feature that are shown */
    public static final int MAX_LINES = 30

    /** The most characters of one line that are shown */
    public static final int MAX_LINE = 200

    // what is never shown: control and format characters, which act on a terminal or are not seen,
    // halves of pairs, characters of private use and the separators that are line breaks of their own
    private static final Pattern NOT_SHOWN = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)

    /** The name of the feature */
    final String feature

    /** The lines to show, each safe to print */
    final List<String> lines

    private PackInstructions(String feature, List<String> lines) {
        this.feature = feature
        this.lines = Collections.unmodifiableList(lines)
    }

    /**
     * @return the lines as one text, with a line feed between them
     */
    String getText() {
        return lines.join('\n')
    }

    /**
     * @param features the features of an application, or those that were added to one
     * @return the instructions of those that are features of packs and have some, in the order of the
     * names of the features
     */
    static List<PackInstructions> of(Collection<? extends Feature> features) {
        List<PackInstructions> all = []
        for (Feature feature : features.toSorted { Feature a, Feature b -> a.getName() <=> b.getName() }) {
            if (feature instanceof PackFeature) {
                PackInstructions instructions = of(((PackFeature) feature).getPack().descriptor)
                if (instructions != null) {
                    all << instructions
                }
            }
        }
        return all
    }

    /**
     * @param descriptor the descriptor of a pack
     * @return its instructions, or {@code null} when it has none
     */
    static PackInstructions of(PackDescriptor descriptor) {
        if (descriptor.instructions == null || descriptor.instructions.isBlank()) {
            return null
        }
        List<String> written = descriptor.instructions.stripTrailing().split('\n', -1).toList()
        List<String> lines = []
        for (String line : written.take(MAX_LINES)) {
            // a tab is a space here: where it would end depends on the terminal
            String safe = NOT_SHOWN.matcher(line.replace('\t', '    ')).replaceAll('?').stripTrailing()
            int count = safe.codePointCount(0, safe.length())
            lines << (count <= MAX_LINE ? safe : safe.substring(0, safe.offsetByCodePoints(0, MAX_LINE)) + '...')
        }
        if (written.size() > MAX_LINES) {
            int more = written.size() - MAX_LINES
            lines << "... and ${more} more ${more == 1 ? 'line' : 'lines'}, which the descriptor of the feature has".toString()
        }
        return new PackInstructions(PackDescriptorParser.shown(descriptor.name, MAX_LINE), lines)
    }
}
