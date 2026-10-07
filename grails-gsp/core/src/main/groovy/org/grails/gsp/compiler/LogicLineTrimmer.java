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
package org.grails.gsp.compiler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Removes the lines of a page that hold only template logic, for a page declaring
 * {@code trimLogicLines="true"}: a line holding nothing but page directives, scriptlets and comments,
 * besides spaces and tabs, writes neither its indentation nor its line break. A line holding text, an
 * expression or a tag is left as it is.
 *
 * <p>The line break of a removed line is moved inside the line's last construct, just after its
 * opening delimiter, rather than dropped, so that every line of the page keeps its number in the
 * compiled page and in the errors reported against it.</p>
 */
final class LogicLineTrimmer {

    private static final String CONSTRUCT = "(?:" +
            "<%--(?:(?!--%>).)*--%>" +          // <%-- comment --%>
            "|%\\{--(?:(?!--\\}%).)*--\\}%" +   // %{-- comment --}%
            "|<%@(?:(?!%>).)*%>" +              // <%@ directive %>
            "|<%(?![=@]|--)(?:(?!%>).)*%>" +    // <% scriptlet %>
            "|%\\{(?!--)(?:(?!\\}%).)*\\}%" +   // %{ scriptlet }%
            ")";

    private static final Pattern CONSTRUCT_PATTERN = Pattern.compile(CONSTRUCT, Pattern.DOTALL);

    private static final Pattern LOGIC_LINE_PATTERN = Pattern.compile(
            "^[ \\t]*(" + CONSTRUCT + "(?:[ \\t]*" + CONSTRUCT + ")*)[ \\t]*(\\r?\\n|\\z)",
            Pattern.MULTILINE | Pattern.DOTALL);

    private LogicLineTrimmer() {
    }

    static String trim(String gspSource) {
        Matcher line = LOGIC_LINE_PATTERN.matcher(gspSource);
        StringBuilder result = new StringBuilder(gspSource.length());
        int copied = 0;
        while (line.find()) {
            result.append(gspSource, copied, line.start());
            appendConstructs(result, line.group(1), line.group(2));
            copied = line.end();
        }
        result.append(gspSource, copied, gspSource.length());
        return result.toString();
    }

    /**
     * Appends the constructs of a logic line without the spaces between them, the line break going
     * just after the opening delimiter of the last one.
     */
    private static void appendConstructs(StringBuilder result, String constructs, String lineBreak) {
        Matcher construct = CONSTRUCT_PATTERN.matcher(constructs);
        String previous = null;
        while (construct.find()) {
            if (previous != null) {
                result.append(previous);
            }
            previous = construct.group();
        }
        if (previous != null) {
            int opening = openingDelimiterLength(previous);
            result.append(previous, 0, opening).append(lineBreak).append(previous, opening, previous.length());
        }
    }

    private static int openingDelimiterLength(String construct) {
        if (construct.startsWith("<%--") || construct.startsWith("%{--")) {
            return 4;
        }
        if (construct.startsWith("<%@")) {
            return 3;
        }
        return 2;
    }
}
