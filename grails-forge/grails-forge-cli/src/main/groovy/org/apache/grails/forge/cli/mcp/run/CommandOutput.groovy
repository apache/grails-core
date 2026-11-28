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
package org.apache.grails.forge.cli.mcp.run

import java.nio.charset.StandardCharsets
import java.util.regex.Pattern
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.ToolResult
import org.apache.grails.forge.cli.mcp.McpNames
import org.apache.grails.forge.cli.mcp.tools.CommandPolicy

/**
 * What a call of {@code grails mcp} gives its client: the result of the command line it ran, made from
 * its exit code and what it wrote.
 *
 * <ul>
 * <li>Exit code 0 is a result that is no error; 1, a command that failed, and 2, a command line that was
 * refused, are a result that is an error, whose lines say what to correct. One exception: a command whose
 * JSON document is a report, as {@code project regenerate --diff --json} with files that differ, ends with
 * 1 although it did what it was asked. Where it wrote its document, and the document has one of the
 * {@linkplain Ran#reportOutcomes outcomes that are a report}, the result is no error; the exit code is in
 * its {@code _meta} all the same. Exit code 1 without such a document, and exit code 2, are errors.</li>
 * <li>A command whose output is text gives one text block: everything it wrote, its output and its errors
 * in the order they were written. A command whose output is a JSON document gives the document as the
 * structured content of the result and as a text block, whole up to {@value #MAX_DOCUMENT_BYTES} bytes, and
 * what it wrote as errors as a second text block.</li>
 * <li>Escape sequences of a terminal, and control characters other than the line feed and the tab, are
 * taken out.</li>
 * <li>A long text is cut: a result that is no error keeps its first {@value #SUCCESS_HEAD} and its last
 * {@value #SUCCESS_TAIL} characters, and one that is an error its first {@value #FAILURE_HEAD} and its last
 * {@value #FAILURE_TAIL}, where the cause of a failure is. A line in the middle says how much was left out,
 * and where the whole output is: in a project, a file of {@code .grails/run/mcp}, which the result
 * links as a resource ({@link OutputLog}).</li>
 * <li>The {@code _meta} of the result has, under {@link McpNames#META_RESULT}, the exit code, the command
 * line that was run with what the server added to it, how long it ran, whether the text was cut, and the
 * URI of the whole output.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
final class CommandOutput {

    /** How many characters at the start of the output of a call that succeeded are kept, when it is cut. */
    public static final int SUCCESS_HEAD = 4_000

    /** How many characters at the end of the output of a call that succeeded are kept, when it is cut. */
    public static final int SUCCESS_TAIL = 28_000

    /** How many characters at the start of the output of a call that failed are kept, when it is cut. */
    public static final int FAILURE_HEAD = 1_000

    /** How many characters at the end of the output of a call that failed are kept, when it is cut: a failure says why at its end. */
    public static final int FAILURE_TAIL = 9_000

    /** The most bytes of a JSON document that is given whole, in UTF-8. */
    public static final int MAX_DOCUMENT_BYTES = 256 * 1024

    /** What the result of {@code create-tool} ends with: the new tool is not offered before the build lists it. */
    public static final String LISTED_AFTER_COMPILE = 'It is listed once the project is compiled: call compile or refresh_tools.'

    /** What a call that was cancelled before it ended gives, where anything is still sent for it. */
    public static final String CANCELLED = 'The call was cancelled before it ended.'

    // CSI and OSC sequences of a terminal, and the other escape sequences of two characters
    private static final Pattern ESCAPES = Pattern.compile('\u001B\\[[0-?]*[ -/]*[@-~]|\u001B\\][^\u0007\u001B]*(?:\u0007|\u001B\\\\)?|\u001B[@-Z\\\\-_]')
    private static final Pattern CONTROLS = Pattern.compile('[\\p{Cc}&&[^\n\t]]')
    private static final String LINE_FEED = '\n'

    private CommandOutput() {
    }

    /**
     * @param text what a command wrote
     * @return the text without the escape sequences of a terminal, and without control characters other
     * than the line feed and the tab; a carriage return before a line feed goes with the rest
     */
    static String cleaned(String text) {
        if (text == null || text.isEmpty()) {
            return ''
        }
        return CONTROLS.matcher(ESCAPES.matcher(text).replaceAll('')).replaceAll('')
    }

    /**
     * Makes the result of a call.
     *
     * @param ran what the call ran, how it ended and what it wrote
     * @return the result, as the protocol's {@code CallToolResult}
     */
    static Map<String, Object> result(Ran ran) {
        Map<String, Object> document = ran.json ? document(ran.output) : null
        boolean failed = ran.exitCode != 0 && !isReport(ran, document)
        int head = failed ? FAILURE_HEAD : SUCCESS_HEAD
        int tail = failed ? FAILURE_TAIL : SUCCESS_TAIL
        String whole = ran.log == null ? 'grails mcp keeps no file of the output outside a project' :
                ran.log.file != null ? "the whole output is ${ran.log.file}".toString() : ran.log.reason
        List<Map<String, Object>> content = []
        boolean truncated = false
        if (document != null) {
            content.add(text(ran.output.trim()))
            String errors = cleaned(ran.errors)
            if (!errors.trim().isEmpty()) {
                Cut cut = cut(errors, head, tail, whole)
                truncated = cut.truncated
                content.add(text(cut.text))
            }
        } else {
            Cut cut = cut(cleaned(ran.merged), head, tail, whole)
            truncated = cut.truncated
            content.add(text(cut.text))
        }
        // the result a tool of the project sets with executionContext.result, read once the tool has ended, is the structured
        // content of the result, and a text block of its JSON, beside what the tool wrote
        Map<String, Object> given = null
        if (document == null && ran.toolResult != null) {
            // the structured content of a result is an object: any other value is the member result of one
            boolean object = ran.toolResult.value instanceof Map
            given = object ? (Map<String, Object>) ran.toolResult.value : [result: ran.toolResult.value] as Map<String, Object>
            content.add(text(object ? ran.toolResult.json : '{"result":' + ran.toolResult.json + '}'))
        }
        if (ran.note != null) {
            Map<String, Object> first = content.first()
            String said = (String) first.get('text')
            first.put('text', said.isEmpty() || said.endsWith(LINE_FEED) ? said + ran.note : said + LINE_FEED + ran.note)
        }
        if (ran.log?.file != null) {
            content.add(link(ran))
        }
        Map<String, Object> result = new LinkedHashMap<>()
        result.put('content', content)
        if (document != null || given != null) {
            result.put('structuredContent', document != null ? document : given)
        }
        result.put('isError', failed)
        Map<String, Object> meta = new LinkedHashMap<>()
        meta.put('exitCode', ran.exitCode)
        meta.put('commandLine', new ArrayList<String>(ran.commandLine))
        meta.put('durationMs', ran.durationMs)
        meta.put('truncated', truncated)
        meta.put('log', ran.log?.uri)
        result.put('_meta', [(McpNames.META_RESULT): meta] as Map<String, Object>)
        return result
    }

    // A command that ended with 1 and wrote the report it was asked for: its document has an outcome that is a report. Any
    // other exit code, and a document with another outcome, as that of a command that could not read the project, is not.
    private static boolean isReport(Ran ran, Map<String, Object> document) {
        return ran.exitCode == 1 && document != null && ran.reportOutcomes.contains(document.get(CommandPolicy.OUTCOME))
    }

    /**
     * @return the result of a call that was cancelled before it ended
     */
    static Map<String, Object> cancelled() {
        Map<String, Object> result = new LinkedHashMap<>()
        result.put('content', [text(CANCELLED)])
        result.put('isError', true)
        return result
    }

    /**
     * Cuts a long text: its start and its end are kept, around one line that says how many lines and
     * characters were left out, and where the whole text is.
     *
     * @param text the text
     * @param head how many characters at its start are kept, at most: the start ends at a line feed where one is
     * @param tail how many characters at its end are kept, at most: the end starts after a line feed where one is
     * @param whole where the whole text is, as the line says it
     * @return the text, cut where it is longer than the two together
     */
    static Cut cut(String text, int head, int tail, String whole) {
        if (text.length() <= head + tail) {
            return new Cut(text, false)
        }
        int start = text.lastIndexOf(LINE_FEED, head - 1)
        int headEnd = start >= Math.floorDiv(head, 2) ? start + 1 : boundary(text, head)
        int end = text.indexOf(LINE_FEED, text.length() - tail)
        int tailStart = end >= 0 && end < text.length() - Math.floorDiv(tail, 2) ? end + 1 : boundary(text, text.length() - tail)
        String left = text.substring(headEnd, tailStart)
        int lines = left.count(LINE_FEED) + (left.endsWith(LINE_FEED) ? 0 : 1)
        String kept = text.substring(0, headEnd)
        String said = "[… ${number(lines)} ${lines == 1 ? 'line' : 'lines'} (${number(left.length())} characters) left out: ${whole}]".toString()
        return new Cut((kept.endsWith(LINE_FEED) ? kept : kept + LINE_FEED) + said + LINE_FEED + text.substring(tailStart), true)
    }

    // the JSON document a command wrote as its output, when it is one object and not too long to give whole
    private static Map<String, Object> document(String output) {
        String trimmed = output == null ? '' : output.trim()
        if (!trimmed.startsWith('{') || trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_DOCUMENT_BYTES) {
            return null
        }
        try {
            Object parsed = new JsonSlurper().parseText(trimmed)
            return parsed instanceof Map ? (Map<String, Object>) parsed : null
        } catch (JsonException | IllegalArgumentException ignored) {
            return null
        }
    }

    private static Map<String, Object> text(String text) {
        Map<String, Object> block = new LinkedHashMap<>()
        block.put('type', 'text')
        block.put('text', text)
        return block
    }

    private static Map<String, Object> link(Ran ran) {
        Map<String, Object> link = new LinkedHashMap<>()
        link.put('type', 'resource_link')
        link.put('uri', ran.log.uri)
        link.put('name', ran.log.file.fileName.toString())
        link.put('title', 'The whole output of the call')
        link.put('description', "Everything ${ran.title} wrote, as it wrote it".toString())
        link.put('mimeType', OutputLog.MIME_TYPE)
        return link
    }

    // a place in the text that does not split a character of two code units
    private static int boundary(String text, int at) {
        return at > 0 && at < text.length() && Character.isLowSurrogate(text.charAt(at)) ? at - 1 : at
    }

    private static String number(int value) {
        return String.format(Locale.ENGLISH, '%,d', value)
    }

    /**
     * A text that may have been cut.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Cut {

        /** The text, cut or whole. */
        final String text

        /** Whether it was cut. */
        final boolean truncated

        Cut(String text, boolean truncated) {
            this.text = text
            this.truncated = truncated
        }
    }

    /**
     * What a call ran, how it ended and what it wrote.
     *
     * @since 9.0
     */
    @CompileStatic
    static final class Ran {

        /** The title of the tool, as {@code grails compile}. */
        String title

        /** What the tool the command line ran gave as its result, {@code executionContext.result}; {@code null} for none. */
        ToolResult toolResult

        /** The command line that was run, with the options the server gives in front of it. */
        List<String> commandLine = []

        /** Its exit code. */
        int exitCode

        /** How long it ran. */
        long durationMs

        /** Everything it wrote, in the order it was written. */
        String merged = ''

        /** What it wrote as its output. */
        String output = ''

        /** What it wrote as its errors. */
        String errors = ''

        /** Whether its output is a JSON document. */
        boolean json

        /**
         * The outcomes of its JSON document that are a report, which make a result that is no error although the
         * command ended with exit code 1; none for most commands.
         */
        List<String> reportOutcomes = []

        /** Where the whole output was kept; {@code null} where nothing was tried, which is outside a project. */
        OutputLog.Kept log

        /** A line the result ends with; {@code null} for none. */
        String note
    }
}
