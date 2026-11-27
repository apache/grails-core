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
package org.apache.grails.forge.cli.mcp.tools

import java.nio.charset.StandardCharsets

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet

/**
 * The samples of {@code help --json} the tools of {@code grails mcp} are made from in these specs, as
 * {@code HelpJsonSamplesSpec} keeps them, and the golden files of the tools made from them, written as
 * that spec writes its samples: an object or an array that holds no other object, and no array of them,
 * is one line, so that a change is a change of the lines it is on.
 */
class McpToolSamples {

    static final String HELP_JSON = 'src/test/resources/mcp/help-json'

    static final String GOLDEN = 'src/test/resources/mcp/tools'

    static final List<String> SAMPLES = ['outside-project', 'web-application', 'application-with-tools'].asImmutable()

    /**
     * The directory of the module, which the tests run in: the directory the JVM was started in, which a
     * relative file is resolved against whatever a spec sets {@code user.dir} to.
     */
    static final File MODULE = new File('').absoluteFile

    static Map<String, Object> sample(String name) {
        return (Map<String, Object>) new JsonSlurper().parse(new File(MODULE, "${HELP_JSON}/${name}.json"), StandardCharsets.UTF_8.name())
    }

    static McpToolSet tools(String name, boolean readOnly = false) {
        return ToolProjection.of(sample(name), readOnly, 'fingerprint')
    }

    static McpTool tool(String sample, String name) {
        McpTool tool = tools(sample).tools.find { it.name == name }
        assert tool != null: "the sample ${sample} has no tool ${name}"
        return tool
    }

    static File golden(String name) {
        return new File(MODULE, "${GOLDEN}/${name}.json")
    }

    /** Every command of a document that has no commands of its own, by its words. */
    static Map<List<String>, Map<String, Object>> leaves(List<Map<String, Object>> commands, List<String> parent = []) {
        Map<List<String>, Map<String, Object>> found = [:]
        commands.each { Map<String, Object> command ->
            List<String> path = parent + [(String) command.get('name')]
            List<Map<String, Object>> below = (List<Map<String, Object>>) command.get('commands') ?: []
            if (below.isEmpty()) {
                found.put(path, command)
            } else {
                found.putAll(leaves(below, path))
            }
        }
        return found
    }

    static String text(Object document) {
        StringBuilder written = new StringBuilder()
        write(written, document, '')
        return written.append('\n').toString()
    }

    // the first line that differs, with the lines around it; null where none does
    static String difference(String expected, String actual) {
        List<String> one = expected.readLines()
        List<String> other = actual.readLines()
        int at = (0..<Math.max(one.size(), other.size())).find { int line -> line >= one.size() || line >= other.size() || one[line] != other[line] } ?: -1
        if (at < 0) {
            return null
        }
        int from = Math.max(0, at - 3)
        return "line ${at + 1} differs; the golden file has:\n${window(one, from, at + 4)}\nand the projection is:\n${window(other, from, at + 4)}".toString()
    }

    private static void write(StringBuilder written, Object value, String indent) {
        Collection<Object> members = value instanceof Map ? ((Map<String, Object>) value).values() : value instanceof List ? (List<Object>) value : null
        if (members == null || !members.any { it instanceof Map || (it instanceof List && ((List) it).any { each -> each instanceof Map || each instanceof List }) }) {
            written.append(JsonOutput.toJson(value))
            return
        }
        String inner = indent + '  '
        boolean object = value instanceof Map
        written.append(object ? '{' : '[').append('\n')
        List<Object> entries = object ? new ArrayList<Object>(((Map<String, Object>) value).entrySet()) : (List<Object>) value
        entries.eachWithIndex { Object entry, int at ->
            written.append(inner)
            if (object) {
                written.append(JsonOutput.toJson(((Map.Entry<String, Object>) entry).key)).append(': ')
                write(written, ((Map.Entry<String, Object>) entry).value, inner)
            } else {
                write(written, entry, inner)
            }
            written.append(at < entries.size() - 1 ? ',\n' : '\n')
        }
        written.append(indent).append(object ? '}' : ']')
    }

    private static String window(List<String> lines, int from, int to) {
        return from >= lines.size() ? '(nothing)' : lines.subList(from, Math.min(to, lines.size())).join('\n')
    }
}
