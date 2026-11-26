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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.apache.grails.gradle.model.cli.ToolCatalogBuilder
import org.apache.grails.gradle.model.cli.ToolDescriptor
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.Unroll

/**
 * The catalog of the tools, as the command line reads it: the member {@code tools}, each tool with its
 * target, {@code application} or {@code standalone}, whether it starts the application, which a tool of
 * the target {@code application} does unless the catalog says {@code false}, and what of it is deprecated.
 * A tool the catalog has otherwise than the task writes it is left out alone, with a line that says why.
 */
class ToolCatalogFileSpec extends CommandSpec implements CommandFixture {

    static final Map APPLICATION = [name: 'report', description: 'Reports', origin: 'application', target: 'application', registration: 'descriptor']

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Unroll
    void "a tool #what is read as one of the target #target"() {
        given:
        catalog([APPLICATION + changed])

        when:
        Map tool = tools().find { it.name == 'report' }

        then:
        tool.target == target
        tool.startsApplication == startsApplication
        tool.registration == registration
        document().diagnostics == []

        where:
        what                                    | changed                                                     || target        | startsApplication | registration
        'that says nothing of the application'  | [:]                                                         || 'application' | true              | 'descriptor'
        'that starts the application'           | [startsApplication: true]                                   || 'application' | true              | 'descriptor'
        'that does not start the application'   | [startsApplication: false]                                  || 'application' | false             | 'descriptor'
        'registered by the factories'           | [registration: 'factories']                                 || 'application' | true              | 'factories'
        'that runs as a process of its own'     | [target: 'standalone']                                      || 'standalone'  | false             | 'descriptor'
        'of its own process, said not to start' | [target: 'standalone', startsApplication: false]            || 'standalone'  | false             | 'descriptor'
    }

    @Unroll
    void "what a tool says is deprecated, #deprecated, is read and described"() {
        given:
        catalog([APPLICATION + [deprecated: deprecated]])

        when:
        Map tool = tools().find { it.name == 'report' }

        then:
        tool.deprecated == deprecated
        document().diagnostics == []

        where:
        deprecated << [['type'], ['folder'], ['type', 'folder'], ['folder', 'type']]
    }

    void "a tool that is deprecated in nothing is described without the member"() {
        given:
        catalog([APPLICATION, APPLICATION + [name: 'empty', deprecated: []]])

        expect:
        tools().every { !it.containsKey('deprecated') }
    }

    @Unroll
    void "a tool #what is left out, and a line says why"() {
        given:
        catalog([APPLICATION + [name: 'fine'], APPLICATION + changed])

        when:
        Map described = document()

        then: 'the other tool is there'
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }*.name == ['fine']
        described.complete == false
        described.diagnostics == ["Tool 2 of ${ToolCatalogFile.LOCATION} is not used: ${why}".toString()]

        where:
        what                                                  | changed                                                  | why
        'of the target gradle, which no longer exists'        | [target: 'gradle', startsApplication: false]             | 'its target is none there is'
        'of the target console'                               | [target: 'console']                                      | 'its target is console, which is not run from here'
        'registered by the bridge of Grails 7, which is gone' | [registration: 'legacy']                                 | 'its registration is none of descriptor, factories'
        'that says it is a script'                            | [registration: 'script']                                 | 'its registration is none of descriptor, factories'
        'that does not say with a boolean'                    | [startsApplication: 'false']                             | 'it does not say with true or false whether it starts the application'
        'of its own process that says it starts it'           | [target: 'standalone', startsApplication: true]          | 'it runs as a process of its own and says that it starts the application'
        'that is deprecated in what there is not'             | [deprecated: ['package']]                                | 'what it says is deprecated is not a list of type and folder, each at most once'
        'that is deprecated twice in one thing'               | [deprecated: ['type', 'type']]                           | 'what it says is deprecated is not a list of type and folder, each at most once'
        'that says what is deprecated in a word'              | [deprecated: 'type']                                     | 'what it says is deprecated is not a list of type and folder, each at most once'
    }

    void "a catalog with a list of commands and none of tools is not used"() {
        given:
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = JsonOutput.toJson([format: 1, complete: true, diagnostics: [], commands: [APPLICATION]])

        when:
        Map described = document()

        then:
        described.complete == false
        ((List<String>) described.diagnostics)[0].startsWith("The tools of the application and its plugins are not listed: ${ToolCatalogFile.LOCATION} has no list of tools.")
        ((List<Map>) described.commands).every { it.origin == 'built-in' }
    }

    void "a script of the application is a tool of the target application that does not start it"() {
        given:
        catalog([APPLICATION])
        File script = new File(dir, 'src/main/scripts/clean-cache.groovy')
        script.parentFile.mkdirs()
        script.text = 'description "Cleans the cache"\n'

        when:
        Map tool = tools().find { it.name == 'clean-cache' }

        then:
        tool.target == 'application'
        tool.startsApplication == false
        tool.registration == 'script'
        tool.origin == 'application'
        !tool.containsKey('deprecated')
    }

    void "what the builder of the catalog writes for each target and each deprecation is read as it is"() {
        given: 'classes registered with descriptors as the compiler writes them, and the catalog the task writes of them'
        ToolCatalogBuilder builder = new ToolCatalogBuilder()
        builder.add(ToolCatalogBuilder.ORIGIN_APPLICATION, element('main', [
                descriptor('example.ReportTool', 'report', ToolDescriptor.TARGET_APPLICATION, true, []),
                descriptor('example.TidyTool', 'tidy', ToolDescriptor.TARGET_APPLICATION, false, ['folder']),
                descriptor('example.OldCommand', 'old', ToolDescriptor.TARGET_APPLICATION, true, ['type', 'folder'])]))
        builder.add('org.example:render-cli-standalone:1.0', element('render', [descriptor('example.RenderTool', 'render', ToolDescriptor.TARGET_STANDALONE, false, [])]))
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.text = builder.toJson()

        when:
        Map<String, Map> tools = tools().collectEntries { [(it.name): it] }

        then:
        document().diagnostics == []
        tools.keySet() == ['old', 'render', 'report', 'tidy'] as Set
        tools.report.subMap(['target', 'startsApplication']) == [target: 'application', startsApplication: true]
        tools.tidy.subMap(['target', 'startsApplication', 'deprecated']) == [target: 'application', startsApplication: false, deprecated: ['folder']]
        tools.old.deprecated == ['type', 'folder']
        tools.render.subMap(['target', 'startsApplication', 'origin']) == [target: 'standalone', startsApplication: false, origin: 'org.example:render-cli-standalone:1.0']
    }

    // the descriptor the compiler writes for a tool, by the class of the tool
    private static Map.Entry<String, String> descriptor(String className, String name, String target, boolean startsApplication, List<String> deprecated) {
        return new AbstractMap.SimpleEntry<String, String>(className, ToolDescriptor.describe(className, name, "The ${name} tool".toString(), target, startsApplication, deprecated))
    }

    // a directory of classes that registers tools under the key the compiler writes, with their descriptors
    private File element(String name, List<Map.Entry<String, String>> descriptors) {
        File element = new File(dir, "build/elements/${name}")
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = "${ToolDescriptor.FACTORIES_KEY_TOOL}=${descriptors*.key.join(',')}\n"
        for (Map.Entry<String, String> each : descriptors) {
            File file = new File(element, ToolDescriptor.location(each.key))
            file.parentFile.mkdirs()
            file.text = each.value
        }
        return element
    }

    private void catalog(List<Map> tools) {
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.bytes = ProjectToolsSpec.doc(tools).getBytes(StandardCharsets.UTF_8)
    }

    private List<Map> tools() {
        return ((List<Map>) document().commands).findAll { it.origin != 'built-in' }
    }

    private Map document() {
        out.reset()
        assert ForgeCli.execute(beanContext, 'help', '--json') == 0
        return (Map) new JsonSlurper().parseText(out.toString().readLines().last())
    }
}
