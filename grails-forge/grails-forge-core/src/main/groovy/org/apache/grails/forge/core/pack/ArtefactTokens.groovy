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
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * The tokens of a template a code generation command writes a class from: text in which
 * {@code @artifact.name@} and the other tokens are replaced by the names of the class. Nothing of
 * such a template is run. The command line renders the templates; what a token is, and which there
 * are, is said here, so that a feature pack is checked against the same tokens when it is read.
 *
 * @since 9.0
 */
@CompileStatic
final class ArtefactTokens {

    /** The declaration of the package of the class, on a line of its own; without a package neither it nor its line is written */
    public static final String PACKAGE = 'artifact.package'

    /** The name of the package of the class, empty for a class without one */
    public static final String PACKAGE_NAME = 'artifact.package.name'

    /** The package of the class as a path, with {@code /} between its steps */
    public static final String PACKAGE_PATH = 'artifact.package.path'

    /** The name of the class, without what the kind of class adds to it */
    public static final String NAME = 'artifact.name'

    /** The name of the class as the name of a property: its first letter in lower case */
    public static final String PROPERTY_NAME = 'artifact.propertyName'

    /** Every token a template can have, without the at signs around it */
    public static final List<String> ALL = List.of(PACKAGE, PACKAGE_NAME, PACKAGE_PATH, NAME, PROPERTY_NAME)

    /** A token, and the end of the line when the token is the last of it */
    public static final Pattern TOKEN = Pattern.compile(/@(artifact\.[A-Za-z0-9_.]*)@(\r\n|\r|\n)?/)

    private static final Pattern LINE_END = Pattern.compile(/\r\n|\r|\n/)
    private static final int MAX_SHOWN = 80

    private ArtefactTokens() {
    }

    /**
     * @param text the text of a template
     * @return what is wrong with the first token of it that is none of {@link #ALL}, with its line;
     * {@code null} when every token is one
     */
    static String problem(String text) {
        Matcher token = TOKEN.matcher(text)
        while (token.find()) {
            if (!ALL.contains(token.group(1))) {
                return "line ${line(text, token.start())}: there is no @${PackDescriptorParser.shown(token.group(1), MAX_SHOWN).replace('@|', '@ |')}@; " +
                        "a template has @${ALL.join('@, @')}@"
            }
        }
        return null
    }

    /**
     * @param text a text
     * @param at a place in it
     * @return the line the place is in, the first being 1: a line ends with a line feed, with a
     * carriage return, or with both
     */
    static int line(String text, int at) {
        Matcher end = LINE_END.matcher(text).region(0, at)
        int line = 1
        while (end.find()) {
            line++
        }
        return line
    }
}
