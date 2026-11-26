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
package org.apache.grails.gradle.model.cli

import java.nio.file.Files
import java.nio.file.attribute.FileTime

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

class StandaloneCommandsDocumentSpec extends Specification {

    @TempDir
    File dir

    def "the commands of a catalog that run as a process of their own are the ones with the target standalone, each with the class path it runs with"() {
        given:
        CommandCatalogBuilder builder = new CommandCatalogBuilder()
        builder.add(CommandCatalogBuilder.ORIGIN_APPLICATION, element('application', [['demo.LocalCommand', 'local', 'standalone'], ['demo.ReportCommand', 'report', 'application']]))
        builder.add('org.example:tool-cli:1.0', element('tool', [['tool.ToolCommand', 'tool', 'standalone'], ['tool.BuildCommand', 'build-tool', 'gradle']]))

        expect:
        StandaloneCommandsDocument.commandsOf(builder.toJson().getBytes('US-ASCII')) == [local: 'application', tool: 'dependencies']
        StandaloneCommandsDocument.commandsOf('not a catalog'.bytes) == [:]
        StandaloneCommandsDocument.commandsOf('{"commands":[{"name":"Not A Name","target":"standalone"},7]}'.bytes) == [:]
    }

    def "a document has the class path of the commands it lists, the same text for the same values"() {
        when:
        String json = StandaloneCommandsDocument.write([local: 'application', tool: 'dependencies'], ['/p/build/classes'], ['/h/a.jar', '/h/b \u00e9.jar'], ['/p/src/main/groovy'], 21, null)

        then:
        json == '{"format":1,"commands":{"local":"application","tool":"dependencies"},"application":["/p/build/classes"],' +
                '"dependencies":["/h/a.jar","/h/b \\u00e9.jar"],"sources":["/p/src/main/groovy"],"java":21}\n'
        ((Map) StrictJson.parse(json, 4)).dependencies == ['/h/a.jar', '/h/b \u00e9.jar']
    }

    def "a document without a command, and one for commands of plugins alone, have no path they do not need"() {
        expect:
        StandaloneCommandsDocument.write([:], ['/p/build/classes'], ['/h/a.jar'], ['/p/src'], 21, 'never said') ==
                '{"format":1,"commands":{},"application":[],"dependencies":[],"sources":[],"java":21}\n'
        StandaloneCommandsDocument.write([tool: 'dependencies'], ['/p/build/classes'], ['/h/a.jar'], ['/p/src'], 21, null) ==
                '{"format":1,"commands":{"tool":"dependencies"},"application":[],"dependencies":["/h/a.jar"],"sources":[],"java":21}\n'
    }

    @Unroll
    def "a class path that #what is not written down, and the document says that the build runs the commands"() {
        when:
        Map document = (Map) StrictJson.parse(StandaloneCommandsDocument.write([tool: 'dependencies', local: 'application'], application, dependencies, sources, 21, given), 4)

        then:
        document.commands == [tool: 'dependencies', local: 'application']
        document.application == []
        document.dependencies == []
        document.sources == []
        document.throughGradle == reason

        where:
        what                                 | application | dependencies                         | sources                           | given                 || reason
        'the build says it runs itself'      | ['/p/c']    | ['/h/a.jar']                         | ['/p/s']                          | "of the\nbuild \u00e9" || 'of the?build ?'
        'has too many entries'               | ['/p/c']    | (1..4096).collect { "/h/${it}.jar" } | ['/p/s']                          | null                  || 'the class path of the commands has more than 4096 entries'
        'has a path that is too long'        | ['/p/c']    | ['/h/' + ('a' * 1024)]               | ['/p/s']                          | null                  || 'a path of the class path of the commands has more than 1024 characters, a control character or the character that separates the entries of a class path'
        'has a path with a line break'       | ['/p/c']    | ['/h/a\n.jar']                       | ['/p/s']                          | null                  || 'a path of the class path of the commands has more than 1024 characters, a control character or the character that separates the entries of a class path'
        'has a path with the separator'      | ['/p/c']    | ['/h/a' + File.pathSeparator + 'b']  | ['/p/s']                          | null                  || 'a path of the class path of the commands has more than 1024 characters, a control character or the character that separates the entries of a class path'
        'is of too many source directories'  | ['/p/c']    | ['/h/a.jar']                         | (1..257).collect { "/p/s${it}" }  | null                  || 'the project has more than 256 directories of sources'
    }

    def "a document is never longer than what reads it reads"() {
        when: 'as many entries as a document has, each as long as a path can be'
        List<String> dependencies = (1..4095).collect { '/h/' + ('a' * 1015) + String.format('%05d', it) }
        String json = StandaloneCommandsDocument.write([tool: 'dependencies'], [], dependencies, [], 21, null)

        then:
        json.length() <= StandaloneCommandsDocument.MAX_BYTES
        ((Map) StrictJson.parse(json, 4)).throughGradle == "the class path of the commands does not fit into ${StandaloneCommandsDocument.MAX_BYTES} bytes"
    }

    def "a source that was changed after an instant is found: a file written again, a new file, and nothing in a directory that is not there"() {
        given:
        File sources = new File(dir, 'src/main/groovy')
        File nested = new File(sources, 'demo')
        nested.mkdirs()
        File source = new File(nested, 'Thing.groovy')
        source.text = 'class Thing { }\n'
        long then = System.currentTimeMillis() - 60_000
        [source, nested, sources]*.setLastModified(then)
        List<String> directories = [new File(dir, 'not/there').path, sources.path]

        expect: 'nothing was changed after the newest of them'
        StandaloneCommandsDocument.changedSince(directories, FileTime.fromMillis(then)) == null

        when: 'a file is written again'
        source.setLastModified(then + 5000)

        then:
        StandaloneCommandsDocument.changedSince(directories, FileTime.fromMillis(then)) == sources.path
        StandaloneCommandsDocument.changedSince(directories, FileTime.fromMillis(then + 5000)) == null

        when: 'a file is added, which changes its directory'
        new File(nested, 'Other.groovy').text = 'class Other { }\n'

        then:
        StandaloneCommandsDocument.changedSince(directories, FileTime.fromMillis(then + 5000)) == sources.path
        StandaloneCommandsDocument.changedSince(directories, FileTime.fromMillis(System.currentTimeMillis() + 60_000)) == null
    }

    private File element(String name, List<List<String>> commands) {
        File element = new File(dir, name)
        new File(element, 'META-INF').mkdirs()
        new File(element, 'META-INF/grails-cli.factories').text = 'org.apache.grails.core.cli.ApplicationCommand=' + commands.collect { it[0] }.join(',') + '\n'
        commands.each { List<String> command ->
            File descriptor = new File(element, CommandDescriptor.location(command[0]))
            descriptor.parentFile.mkdirs()
            descriptor.text = CommandDescriptor.describe(command[0], command[1], 'Does it', command[2])
        }
        element
    }
}
