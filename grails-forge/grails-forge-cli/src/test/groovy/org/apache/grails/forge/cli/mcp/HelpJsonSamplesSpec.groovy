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
package org.apache.grails.forge.cli.mcp

import java.nio.charset.StandardCharsets

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.forge.cli.console.CommandCatalog
import org.apache.grails.forge.cli.console.CommandStreams
import org.apache.grails.forge.cli.console.ToolCatalogFile
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.ForgeContext
import spock.lang.Unroll

/**
 * The samples of {@code help --json} under {@code src/test/resources/mcp/help-json}, which what makes
 * the tools of {@code grails mcp} from the document is tested with, are what the command line writes,
 * as {@code grails mcp} runs it: in a directory that is in no project, in a web application, and in a web
 * application with tools of its own and of its plugins, one of each kind there is for an agent. The
 * directory of the project is compared as {@code <project>}; everything else is compared as it is written.
 *
 * <p>The tools of the third sample declare their options, parameters and hints, read what is typed, or
 * lack a description, as the catalog of the tools of the project has them: in the form the task that
 * writes the catalog gives them, with only the members that say something. {@code help --json} describes
 * them with every member, as it describes the options and parameters of a command of the command line.</p>
 *
 * <p>After a change of {@code help --json} that is meant, write the samples again by running this spec
 * with the environment variable {@code FORGE_UPDATE_GOLDEN} set to {@code true}, and read the difference
 * before it is committed, with the tests that read the samples.</p>
 */
class HelpJsonSamplesSpec extends CommandSpec implements CommandFixture {

    static final String SAMPLES = 'src/test/resources/mcp/help-json'

    static final String PROJECT = '<project>'

    static final String DESCRIPTOR = 'descriptor'

    static final String APPLICATION = 'application'

    static final Map<String, Boolean> DEFAULT_HINTS = [readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: true].asImmutable()

    // The tools of the third sample, as the catalog of the tools of a project lists them: a tool of the application that
    // declares everything, one that reads what is typed, one that declares nothing, a tool of a plugin of Grails, a tool
    // of another plugin that says it changes nothing, and a standalone tool of a plugin of Grails. Their options are in the
    // order they are declared, which is not the order help --json gives them in.
    static final List<Map<String, Object>> TOOLS = [
            [name       : 'audit-report', aliases: [], description: 'Writes the audit report', origin: APPLICATION, target: APPLICATION, startsApplication: true,
             registration: DESCRIPTOR,
             options    : [option(['-s', '--since'], '1', 'string', 'DATE', 'The first day of the report', null, null),
                           option(['--format'], '1', 'string', 'FORMAT', 'The format of the report', ['TEXT', 'JSON'], 'TEXT'),
                           option(['--include-deleted'], '0', 'boolean', null, 'Lists deleted records as well', null, null)],
             parameters : [parameter('OUTPUT', '1', 'The file to write')],
             hints      : DEFAULT_HINTS + [readOnlyHint: true]],
            [name       : 'import-data', aliases: [], description: 'Imports the records the user types in', origin: APPLICATION, target: APPLICATION, startsApplication: true,
             registration: DESCRIPTOR, hints: [readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false], readsInput: true],
            [name       : 'legacy-task', aliases: [], origin: APPLICATION, target: APPLICATION, startsApplication: false, registration: DESCRIPTOR,
             incomplete : ['description', 'arguments', 'hints']],
            [name       : 'dbm-update', aliases: [], description: 'Updates the database to the current version of the change log',
             origin     : 'org.apache.grails:grails-data-hibernate7-dbmigration-cli:9.0.0', target: APPLICATION, startsApplication: true, registration: DESCRIPTOR,
             options    : [option(['--dataSource'], '1', 'string', 'DATASOURCE', 'The data source to update', null, null),
                           option(['--defaultSchema'], '1', 'string', 'SCHEMA', 'The schema to update', null, null),
                           option(['--contexts'], '1', 'string', 'CONTEXTS', 'The contexts of the change sets to run', null, null)],
             hints      : DEFAULT_HINTS],
            [name       : 'example-report', aliases: [], description: 'Reports on the example data', origin: 'org.example:example-tools-cli:1.0.0', target: APPLICATION,
             startsApplication: false, registration: DESCRIPTOR,
             options    : [option(['--verbose'], '0', 'boolean', null, 'Says more', null, null)],
             hints      : [readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false]],
            [name       : 'generate-all', aliases: [], description: 'Generates a controller and views for a domain class',
             origin     : 'org.apache.grails:grails-scaffolding-cli-standalone:9.0.0', target: 'standalone', startsApplication: false, registration: DESCRIPTOR,
             options    : [option(['-f', '--force'], '0', 'boolean', null, 'Replaces the files that are there', null, null)],
             parameters : [parameter('DOMAIN-CLASS', '1', 'The domain class to generate for')],
             hints      : DEFAULT_HINTS + [openWorldHint: false]]
    ].asImmutable()

    @Unroll
    void "the sample #sample is what help --json writes #where"() {
        given:
        prepare(sample)
        CommandStreams streams = CommandStreams.capture()

        when:
        int exit = ForgeCli.execute(ForgeContext.of(beanContext), streams, '--plain-output', '--non-interactive', 'help', '--json')
        Map<String, Object> document = (Map<String, Object>) comparable(new JsonSlurper().parseText(streams.standardOutput()))
        String written = text(document)
        File file = new File(previousUsrDir, "${SAMPLES}/${sample}.json")
        if (System.getenv('FORGE_UPDATE_GOLDEN') == 'true' && !System.getenv('CI')) {
            file.parentFile.mkdirs()
            file.setText(written, StandardCharsets.UTF_8.name())
        }

        then: 'the document is all that is written'
        exit == 0
        streams.standardError() == ''
        streams.standardOutput().readLines().size() == 1

        and:
        file.isFile()
        difference(file.getText(StandardCharsets.UTF_8.name()), written) == null

        and: 'what each sample is there for'
        document.projectDirectory == (sample == 'outside-project' ? null : PROJECT)
        sample != 'application-with-tools' || (document.complete && document.diagnostics == [])
        sample != 'application-with-tools' || tools(document).keySet() == (TOOLS*.name + ['clean-cache']) as Set

        where:
        sample                   | where
        'outside-project'        | 'in a directory that is in no project'
        'web-application'        | 'in a web application whose build has not listed its tools'
        'application-with-tools' | 'in a web application with tools of its own and of its plugins, and a script'
    }

    void "a command line that is run with streams of its own writes there, and not to the output of the process"() {
        given:
        PrintStream out = System.out
        PrintStream err = System.err
        ByteArrayOutputStream process = new ByteArrayOutputStream()
        System.setOut(new PrintStream(process, true))
        System.setErr(new PrintStream(process, true))
        CommandStreams document = CommandStreams.capture()
        CommandStreams mistake = CommandStreams.capture()

        when:
        int described = ForgeCli.execute(ForgeContext.of(beanContext), document, '--plain-output', '--non-interactive', 'help', '--json')
        int refused = ForgeCli.execute(ForgeContext.of(beanContext), mistake, '--plain-output', '--non-interactive', 'no-such-command')

        then:
        described == 0
        ((Map) new JsonSlurper().parseText(document.standardOutput())).format == 1
        refused == 2
        mistake.standardOutput() == ''
        mistake.standardError().contains('There is no command no-such-command')
        mistake.merged() == mistake.standardError()

        and:
        process.size() == 0

        cleanup:
        System.setOut(out)
        System.setErr(err)
    }

    private void prepare(String sample) {
        if (sample == 'outside-project') {
            return
        }
        generateProject(ApplicationType.WEB)
        if (sample == 'application-with-tools') {
            File script = new File(dir, 'src/main/scripts/clean-cache.groovy')
            script.parentFile.mkdirs()
            script.text = ''
            File catalog = new File(dir, ToolCatalogFile.LOCATION)
            catalog.parentFile.mkdirs()
            catalog.setText(JsonOutput.toJson([format: 1, complete: true, diagnostics: [], tools: TOOLS]), StandardCharsets.US_ASCII.name())
            // listed after everything the build lists the tools from
            catalog.setLastModified(System.currentTimeMillis() + 60_000)
        }
    }

    // the document with the directory of the project as <project>, wherever it is said
    private Object comparable(Object value) {
        if (value instanceof Map) {
            return ((Map<String, Object>) value).collectEntries { String member, Object each -> [(member): comparable(each)] }
        }
        if (value instanceof List) {
            return ((List<Object>) value).collect { comparable(it) }
        }
        if (value instanceof String) {
            String text = (String) value
            for (String path : [dir.toPath().toRealPath().toString(), dir.canonicalPath, dir.absolutePath]) {
                text = text.replace(path, PROJECT)
            }
            return text
        }
        return value
    }

    // An object or an array that holds no other object, and no array of them, is one line, as an option is; any other
    // has a line for each of its members, so that a change of the document is a change of the lines it is on.
    private static String text(Object document) {
        StringBuilder written = new StringBuilder()
        write(written, document, '')
        return written.append('\n').toString()
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

    // the first line that differs, with the lines around it; null where none does
    private static String difference(String expected, String actual) {
        List<String> one = expected.readLines()
        List<String> other = actual.readLines()
        int at = (0..<Math.max(one.size(), other.size())).find { int line -> line >= one.size() || line >= other.size() || one[line] != other[line] } ?: -1
        if (at < 0) {
            return null
        }
        int from = Math.max(0, at - 3)
        return "line ${at + 1} differs; the sample has:\n${window(one, from, at + 4)}\nand help --json writes:\n${window(other, from, at + 4)}".toString()
    }

    private static String window(List<String> lines, int from, int to) {
        return from >= lines.size() ? '(nothing)' : lines.subList(from, Math.min(to, lines.size())).join('\n')
    }

    private static Map<String, Map> tools(Map document) {
        return ((List<Map>) document.commands).findAll { it.origin != CommandCatalog.BUILT_IN && it.project }.collectEntries { [(it.name): it] }
    }

    // an option as the task that writes the catalog writes one: only the members that say something
    private static Map<String, Object> option(List<String> names, String arity, String type, String label, String description, List<String> choices, String defaultValue) {
        Map<String, Object> option = [names: names, arity: arity, type: type]
        [label: label, description: description, choices: choices, default: defaultValue].each { String member, Object value ->
            if (value != null) {
                option.put(member, value)
            }
        }
        return option
    }

    private static Map<String, Object> parameter(String label, String arity, String description) {
        return [label: label, arity: arity, type: 'string', description: description]
    }
}
