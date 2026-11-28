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
package org.grails.forge.feature

import groovy.transform.CompileStatic
import java.util.regex.Pattern
import org.grails.forge.options.OptionName

/**
 * The features and options that were asked for cannot be had together, or one of them is not there:
 * a mistake of whoever asked, found when the features are selected and before any of them is applied.
 * It is an {@link IllegalArgumentException} as every refusal of a selection has been.
 *
 * @since 9.0
 */
@CompileStatic
class FeatureSelectionException extends IllegalArgumentException {

    /** The longest name a message shows; what is longer is cut. */
    public static final int MAX_SHOWN = 80

    // control, format and private-use characters, unpaired surrogates, line and paragraph separators
    private static final Pattern UNSAFE = Pattern.compile(/[\p{Cc}\p{Cf}\p{Cs}\p{Co}\p{Zl}\p{Zp}]/)
    private static final int MAX_LINKS = 3

    private final OptionName option
    private final String without
    private final String with

    FeatureSelectionException(String message) {
        this(message, null, null, null, null)
    }

    FeatureSelectionException(String message, Throwable cause) {
        this(message, cause, null, null, null)
    }

    FeatureSelectionException(String message, Throwable cause, OptionName option, String without, String with) {
        super(message, cause)
        this.option = option
        this.without = without
        this.with = with
    }

    /**
     * @return the option that was given and does not agree with the features, or {@code null} when the
     * refusal is not about an option
     */
    OptionName getOption() {
        return option
    }

    /**
     * @return what the application would be without although the option asks for it, as a message
     * says it, or {@code null}
     */
    String getWithout() {
        return without
    }

    /**
     * @return what the application would have although the option asks for none, as a message says it,
     * or {@code null}
     */
    String getWith() {
        return with
    }

    /**
     * A name that was given by whoever asked, as a message shows it: one line, without characters that
     * act on a terminal, not read as markup by a command line that styles its messages, and no longer
     * than {@link #MAX_SHOWN} characters.
     *
     * @param name what was given, which need not be the name of anything
     * @return the name to put into a message
     */
    static String shown(String name) {
        if (name == null || name.isEmpty()) {
            return '(empty)'
        }
        // cut before it is searched, so a long name costs no more than a short one
        String start = name.length() > MAX_SHOWN * 2 ? name.substring(0, MAX_SHOWN * 2) : name
        String safe = UNSAFE.matcher(start).replaceAll('?').replace('@|', '@ |')
        int count = safe.codePointCount(0, safe.length())
        if (start.length() == name.length() && count <= MAX_SHOWN) {
            return safe
        }
        return safe.substring(0, safe.offsetByCodePoints(0, Math.min(MAX_SHOWN, count))) + '...'
    }

    /**
     * The way from one feature to another through what each requires, for a message: the first and
     * the last few of a long way, with the number of those left out between them.
     *
     * @param names the names of the features on the way, in order
     * @return {@code a requires b requires c}
     */
    static String chain(List<String> names) {
        if (names.size() <= MAX_LINKS * 2) {
            return names.join(' requires ')
        }
        return names.take(MAX_LINKS).join(' requires ') + " requires ... ${names.size() - MAX_LINKS * 2} more ... requires " +
                names.takeRight(MAX_LINKS).join(' requires ')
    }
}
