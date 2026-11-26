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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Predicate
import javax.tools.ToolProvider
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * A tool the build of a project lists with the target {@code standalone} is started by the CLI
 * as a process of its own, with the class path the build wrote down for it, and without Gradle: the
 * Gradle wrapper of the project here is a script that writes down what it was called with, and is
 * called only where the specification says so. The class the process starts is a stand-in that says
 * what it was started with; the real one is specified where it is, in {@code grails-core}.
 *
 * <p>The file the build writes is made from the sources of {@code src/standalone} and from what they
 * depend on, and from nothing of the application: a change to the application, to {@code src/cli} or
 * to the catalog of the tools has no build run for such a tool, which runs while the application
 * does not compile.</p>
 */
@IgnoreIf({ os.windows })
class StandaloneToolsSpec extends CommandSpec implements CommandFixture {

    static final Map TOOL = [name: 'tool', aliases: [], description: 'Renders a file', origin: 'org.example:tool-cli-standalone:1.0', target: 'standalone', registration: 'descriptor']
    static final Map LOCAL = [name: 'local', aliases: [], description: 'Of the application', origin: 'application', target: 'standalone', registration: 'descriptor']
    static final Map BUILD_TOOL = [name: 'build-tool', aliases: [], description: 'Needs the build', origin: 'org.example:tool-cli:1.0', target: 'application', startsApplication: false,
                                   registration: 'descriptor']
    static final String NOT_RUN = 'The tool was not run; the build runs it with: grails-forge-cli run-tool tool'

    static final String RUNNER = '''\
        package org.apache.grails.core.cli;

        public class StandaloneToolRunner {

            public static void main(String[] args) throws Exception {
                System.out.println("arguments=" + args.length);
                String file = args[0].substring("--grails-args-file=".length());
                String vector = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)), "UTF-8");
                System.out.println("vector=" + vector);
                System.out.println("system=" + System.getProperty("grails.env") + " " + System.getProperty("grails.full.stacktrace")
                        + " " + System.getProperty("grails.console.enable.terminal") + " " + System.getProperty("grails.console.enable.interactive")
                        + " " + System.getenv("GRAILS_NON_INTERACTIVE"));
                System.out.println("base=" + System.getProperty("base.dir"));
                System.out.println("slf4j=" + System.getProperty("slf4j.internal.verbosity"));
                System.out.println("directory=" + new java.io.File("").getAbsolutePath());
                System.out.println("classpath=" + System.getProperty("java.class.path"));
                if (vector.contains("\\"read-input\\"")) {
                    System.out.println("input=" + System.in.read());
                }
                if (vector.contains("\\"fail\\"")) {
                    System.exit(1);
                }
                if (vector.contains("\\"seven\\"")) {
                    System.exit(7);
                }
            }
        }
        '''.stripIndent()

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File ran
    File classes
    File library
    File away

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        dir = dir.canonicalFile
        System.setProperty('user.dir', dir.absolutePath)
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'src/main/groovy').mkdirs()
        new File(dir, 'src/main/groovy/Thing.groovy').text = 'class Thing { }\n'
        new File(dir, 'src/standalone/groovy').mkdirs()
        new File(dir, 'src/standalone/groovy/LocalTool.groovy').text = 'class LocalTool { }\n'
        ran = new File(dir, 'wrapper-ran')
        // the wrapper writes down what it was called with, and then does what a specification has it do
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\nhere="$(dirname "$0")"\nprintf \'%s\\n\' "$@" >> "$here/wrapper-ran"\nif [ -f "$here/when-built.sh" ]; then . "$here/when-built.sh"; fi\n'
        wrapper.setExecutable(true, false)
        // the output of the source set of the standalone tools, which is what of the project their class path has
        classes = new File(dir, 'build/classes/groovy/standalone')
        classes.mkdirs()
        library = new File(dir, 'lib/library')
        library.mkdirs()
        File source = new File(dir, 'build/StandaloneToolRunner.java')
        source.text = RUNNER
        assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', library.path, source.path) == 0
        GradleBuild.setNonInteractive(false)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
        StandaloneToolsFile.setPlaces(null)
        StandaloneToolProcess.setJavaHome(null)
        GradleBuild.setNonInteractive(false)
        // a directory a feature took the permissions of is deleted with the others
        new File(dir, '.grails/cache').setWritable(true, true)
        away?.deleteDir()
    }

    void "a standalone tool of a plugin is started as a process with the class path the build wrote down, and Gradle is not run"() {
        given:
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool', 'two words', '--flag=a value', '-x', '--', '$HOME')

        then: 'the tool ran with what it was given, each argument as it is, in the directory of the project'
        exit == 0
        line('arguments=') == '1'
        new JsonSlurper().parseText(line('vector=')) == ['tool', 'two words', '--flag=a value', '-x', '--', '$HOME']
        line('system=') == 'development null null null null'
        line('base=') == dir.path
        line('directory=') == dir.path

        and: 'SLF4J, which has no logging backend there, says nothing of it, and only its errors'
        line('slf4j=') == 'ERROR'

        and: 'with the class path the file names, the output of src/standalone before what the tools depend on'
        line('classpath=') == [classes.path, library.path].join(File.pathSeparator)

        and: 'one line said what runs and how, and nothing ran the build'
        err.toString().readLines() == ['| Running the tool tool of org.example:tool-cli-standalone:1.0, as a process of its own, without Gradle']
        !ran.exists()

        and: 'nothing of the run is left behind'
        new File(dir, '.grails/run').list().toList() - ['.gitignore'] == []
    }

    void "a standalone tool of the application is started with the classes of src/standalone before what it depends on"() {
        given:
        listed([LOCAL])
        written([local: 'application'])

        when:
        int exit = ForgeCli.execute(beanContext, 'local')

        then:
        exit == 0
        line('classpath=') == [classes.path, library.path].join(File.pathSeparator)
        err.toString().readLines() == ['| Running the tool local of the application, as a process of its own, without Gradle']
        !ran.exists()
    }

    @Unroll
    void "a standalone tool that ends with #code ends the command line with 1, after one line"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool', argument)

        then:
        exit == 1
        err.toString().readLines().last().endsWith("The command did not succeed: it ended with exit code ${code}")
        !ran.exists()

        where:
        argument | code
        'fail'   | 1
        'seven'  | 7
    }

    void "what the command line was asked for reaches the process: the whole stack trace, plain output, and that nothing may wait for what is typed"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, '--stacktrace', '--plain-output', '--non-interactive', 'tool', 'read-input')

        then:
        exit == 0
        line('system=') == 'development true false false true'
        // an input that has ended
        line('input=') == '-1'
        !ran.exists()
    }

    @Unroll
    void "the build runs once, and then the tool without it, when #what"() {
        given: 'a build that writes the two files as the tasks do'
        listed([TOOL, LOCAL])
        written([tool: 'dependencies', local: 'application'])
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        new File(dir, 'catalog.json').bytes = catalog.bytes
        new File(dir, 'standalone.json').bytes = file.bytes
        new File(dir, 'when-built.sh').text = 'mkdir -p "$here/lib/library"\ncp "$here/catalog.json" "$here/.grails/cache/tool-catalog.json"\n' +
                'cp "$here/standalone.json" "$here/.grails/cache/standalone-tools.json"\n'
        change.call(this)

        when:
        int exit = ForgeCli.execute(beanContext, command, 'argument')

        then: 'one line says why, the build ran the task that writes the file, and then the tool ran'
        exit == 0
        err.toString().readLines()[0] == "| Building the standalone tools of the project before ${command} is run, with the Gradle task grailsStandaloneTools: ${reason}"
        err.toString().readLines().last() == "| Running the tool ${command} of ${command == 'local' ? 'the application' : 'org.example:tool-cli-standalone:1.0'}, as a process of its own, without Gradle"
        ran.readLines().count { String argument -> argument.endsWith('grailsStandaloneTools') } == 1
        !ran.readLines().any { String argument -> argument.endsWith('runTool') || argument.endsWith('grailsToolCatalog') }
        new JsonSlurper().parseText(line('vector=')) == [command, 'argument']

        when: 'it is run again, and nothing has changed'
        ran.delete()
        out.reset()
        err.reset()
        exit = ForgeCli.execute(beanContext, command, 'argument')

        then: 'Gradle is not run'
        exit == 0
        !ran.exists()
        err.toString().readLines().size() == 1

        where:
        what                                                       | command | reason                                                                                      | change
        'the file is not there'                                    | 'tool'  | 'there is no .grails/cache/standalone-tools.json'                                           | { StandaloneToolsSpec spec -> new File(spec.dir, StandaloneToolsFile.LOCATION).delete() }
        'a build file is newer than the file'                      | 'tool'  | 'build.gradle is newer than .grails/cache/standalone-tools.json'                            | { StandaloneToolsSpec spec -> new File(spec.dir, 'build.gradle').text = '// changed\n'; spec.older(StandaloneToolsFile.LOCATION, 'build.gradle') }
        'the settings of the build are newer than the file'        | 'local' | 'settings.gradle.kts is newer than .grails/cache/standalone-tools.json'                     | { StandaloneToolsSpec spec -> new File(spec.dir, 'settings.gradle.kts').text = '// changed\n'; spec.older(StandaloneToolsFile.LOCATION, 'settings.gradle.kts') }
        'an entry of the class path is no longer there'            | 'tool'  | 'lib/library of the class path is no longer there'                                          | { StandaloneToolsSpec spec -> spec.moveLibraryAway() }
        'a source of src/standalone is newer than the file'        | 'local' | 'src/standalone was changed after .grails/cache/standalone-tools.json was written'          | { StandaloneToolsSpec spec -> spec.older(StandaloneToolsFile.LOCATION, 'src/standalone/groovy/LocalTool.groovy') }
        'a source of src/standalone is newer, for a plugin tool'   | 'tool'  | 'src/standalone was changed after .grails/cache/standalone-tools.json was written'          | { StandaloneToolsSpec spec -> spec.older(StandaloneToolsFile.LOCATION, 'src/standalone/groovy/LocalTool.groovy') }
        'a new source of src/standalone'                           | 'local' | 'src/standalone was changed after .grails/cache/standalone-tools.json was written'          | { StandaloneToolsSpec spec -> spec.created('src/standalone/groovy/example/OtherTool.groovy') }
        'the file does not list the tool'                          | 'tool'  | '.grails/cache/standalone-tools.json does not have the tool'                                | { StandaloneToolsSpec spec -> spec.written([local: 'application']) }
        'the file is not the document the build writes'            | 'tool'  | '.grails/cache/standalone-tools.json is not the document of format 1 the build writes'      | { StandaloneToolsSpec spec -> new File(spec.dir, StandaloneToolsFile.LOCATION).text = '{"format":2}\n' }
    }

    @Unroll
    void "nothing of the application is built for a standalone tool when #what"() {
        given: 'a file the build wrote, and then a change to what the tool is not made from'
        listed([TOOL, LOCAL])
        written([tool: 'dependencies', local: 'application'])
        change.call(this)

        when:
        int exit = ForgeCli.execute(beanContext, command)

        then: 'the tool runs at once, and Gradle is not run'
        exit == 0
        err.toString().readLines() == ["| Running the tool ${command} of ${command == 'local' ? 'the application' : 'org.example:tool-cli-standalone:1.0'}, as a process of its own, without Gradle"]
        !ran.exists()

        where:
        what                                                                       | command | change
        'a source of the application is newer than the file'                       | 'local' | { StandaloneToolsSpec spec -> spec.older(StandaloneToolsFile.LOCATION, 'src/main/groovy/Thing.groovy') }
        'a source of the application does not compile'                             | 'local' | { StandaloneToolsSpec spec -> spec.added('src/main/groovy/Broken.groovy', 'class Broken {') }
        'a tool of src/cli is newer than the file'                                 | 'tool'  | { StandaloneToolsSpec spec -> spec.added('src/cli/groovy/example/ReportTool.groovy') }
        'a source in grails-app/commands is newer than the file'                   | 'local' | { StandaloneToolsSpec spec -> spec.added('grails-app/commands/example/OldCommand.groovy') }
        'the catalog is newer than the file'                                       | 'tool'  | { StandaloneToolsSpec spec -> spec.older(StandaloneToolsFile.LOCATION, ToolCatalogFile.LOCATION) }
        'the catalog may be out of date, for a change of src/cli'                  | 'local' | { StandaloneToolsSpec spec -> spec.older(ToolCatalogFile.LOCATION, 'src/main/groovy/Thing.groovy'); spec.added('src/cli/groovy/example/ReportTool.groovy') }
    }

    void "a build that leaves the file as it was, and one that fails, end the tool without running it"() {
        given: 'a build that writes nothing'
        listed([TOOL])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        ran.readLines().count { String argument -> argument.endsWith('grailsStandaloneTools') } == 1
        out.toString().isEmpty()
        err.toString().readLines().last().endsWith('The build of the project did not write down a class path for the tool tool: there is no .grails/cache/standalone-tools.json. ' +
                NOT_RUN)

        when: 'a build that fails'
        new File(dir, 'when-built.sh').text = 'exit 3\n'
        ran.delete()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().last().endsWith('The standalone tools of the project could not be built, so the tool tool was not run')
    }

    @Unroll
    void "nothing is started, and Gradle is not run, when #what"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])
        change.call(this)

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')
        String said = err.toString().readLines().last()

        then:
        exit == 1
        out.toString().isEmpty()
        !ran.exists()
        said.contains('The tool tool is not started as a process of its own: ')
        said.contains(reason)
        said.endsWith(NOT_RUN)

        where:
        what                                                   | reason                                                                                   | change
        'the file is a link'                                   | '.grails/cache/standalone-tools.json is, or is behind, a link or is not a file'          | { StandaloneToolsSpec spec -> spec.linked() }
        'the file is not the user\'s own'                      | 'standalone-tools.json is not owned by the user who runs the command'                    | { StandaloneToolsSpec spec -> spec.notOwned('standalone-tools.json') }
        'the catalog of the tools is not the user\'s own'      | 'tool-catalog.json is not owned by the user who runs the command'                        | { StandaloneToolsSpec spec -> spec.notOwned('tool-catalog.json') }
        'everybody can write to the directory of the file'     | '.grails/cache can be written to by everybody'                                           | { StandaloneToolsSpec spec -> spec.writableByEverybody(new File(spec.dir, '.grails/cache')) }
        'an entry of the class path is outside the build'      | 'is not within the build of the project, the Gradle user home or the local Maven repository' | { StandaloneToolsSpec spec -> spec.written([tool: 'dependencies'], [spec.outside().path]) }
        'an entry of the class path is not a whole path'       | 'is not a whole path that names its file itself'                                         | { StandaloneToolsSpec spec -> spec.written([tool: 'dependencies'], ['lib/library']) }
        'an entry of the class path names its file by a way up' | 'is not a whole path that names its file itself'                                        | { StandaloneToolsSpec spec -> spec.written([tool: 'dependencies'], [new File(spec.dir, 'lib/../lib/library').path]) }
        'everybody can write to an entry of the class path'    | 'lib/library can be written to by everybody'                                             | { StandaloneToolsSpec spec -> spec.writableByEverybody(spec.library) }
        'everybody can write to a directory above an entry'    | 'lib can be written to by everybody'                                                     | { StandaloneToolsSpec spec -> spec.writableByEverybody(new File(spec.dir, 'lib')) }
        'an entry of the class path is another user\'s'        | 'library is owned neither by the user who runs the command nor by root'                  | { StandaloneToolsSpec spec -> spec.notOwned('library') }
    }

    void "an entry of the class path in a directory the user named as safe is used whoever owns it"() {
        given:
        listed([TOOL])
        File outside = outside()
        written([tool: 'dependencies'], [outside.path])
        ConsoleProject.setSafeDirectories(outside.parentFile.canonicalPath)

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 0
        line('classpath=') == [classes.path, outside.path].join(File.pathSeparator)
        !ran.exists()
    }

    void "entries in a repository that is elsewhere name the directory to put in GRAILS_SAFE_DIRECTORY, and the tool runs once it is there"() {
        given: 'two entries of one repository that is in none of the places an entry can be in'
        listed([TOOL])
        StandaloneToolsFile.setPlaces([])
        away = Files.createTempDirectory('grailsoutside').toFile().canonicalFile
        File repository = new File(away, 'repository')
        File first = new File(repository, 'org/example/tool/1.0/classes')
        File second = new File(repository, 'cloud/other/lib/2.0/classes')
        assert first.parentFile.mkdirs() && second.mkdirs()
        assert library.renameTo(first)
        written([tool: 'dependencies'], [first.path, second.path])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')
        String said = err.toString().readLines().last()

        then: 'nothing is started, and the line says what to name and what runs the tool meanwhile'
        exit == 1
        out.toString().isEmpty()
        !ran.exists()
        said.contains('as a file of a repository that is a directory (maven { url = file(...) }) or of a local Maven repository in another place is. ' +
                "To use what is in it, name ${repository.path} in the environment variable GRAILS_SAFE_DIRECTORY. ")
        said.endsWith(NOT_RUN)

        when: 'the directory is named'
        ConsoleProject.setSafeDirectories(repository.path)
        err.reset()
        exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 0
        line('classpath=') == [classes.path, first.path, second.path].join(File.pathSeparator)
        !ran.exists()
    }

    void "a class path is taken for no tool the catalog does not say is a standalone tool"() {
        given: 'a file that names a tool the build runs'
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies', 'build-tool': 'dependencies'])

        when:
        ForgeCli.execute(beanContext, 'build-tool')

        then: 'it is run by the build, as the catalog says'
        out.toString().isEmpty()
        ran.readLines().any { String argument -> argument.endsWith('runTool') }
        err.toString().readLines()[0] == '| Running the tool build-tool of org.example:tool-cli:1.0, with the Gradle task runTool'
    }

    void "a tool the build says it runs itself is run with Gradle, after a line that says why"() {
        given:
        listed([TOOL])
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        file.text = JsonOutput.toJson([format: 1, tools: ['tool'], classpath: [], sources: [], java: 21,
                                       throughGradle: 'their class path has the output of another project of the build (:lib), which only the build keeps current']) + '\n'

        when:
        ForgeCli.execute(beanContext, 'tool', 'argument')

        then:
        out.toString().isEmpty()
        err.toString().readLines()[0..1] == [
                '| The tool tool is run with Gradle: their class path has the output of another project of the build (:lib), which only the build keeps current',
                '| Running the tool tool of org.example:tool-cli-standalone:1.0, with the Gradle task runTool']
        ran.readLines().any { String argument -> argument.endsWith('runTool') }
    }

    void "run-tool runs a standalone tool with Gradle, as it runs every tool"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        ForgeCli.execute(beanContext, 'run-tool', '-Dgrails.env=production', 'tool', 'argument')

        then:
        out.toString().isEmpty()
        err.toString().readLines()[0] == '| Running the tool tool of org.example:tool-cli-standalone:1.0, with the Gradle task runTool'
        ran.readLines().any { String argument -> argument.endsWith('runTool') }
        ran.readLines().contains('-Dgrails.env=production')
    }

    void "the environment after the name of a standalone tool is an argument of the tool, and a warning says so"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool', '-Dgrails.env=production')

        then:
        exit == 0
        line('system=').startsWith('development ')
        new JsonSlurper().parseText(line('vector=')) == ['tool', '-Dgrails.env=production']
        err.toString().readLines().any { String line ->
            line == '| Warning -Dgrails.env=... after the name of tool is given to the tool as an argument, and the tool runs for the environment development. ' +
                    'To run it for another environment, name it before the tool: grails-forge-cli run-tool -Dgrails.env=production tool'
        }
        !ran.exists()
    }

    void "a Java that is older than the classes of the project, and a JAVA_HOME that names none, start nothing"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'], [library.path], 99)

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().last().contains('The classes of the project are compiled for Java 99, and ')
        err.toString().readLines().last().endsWith(NOT_RUN)
        !ran.exists()

        when:
        written([tool: 'dependencies'])
        StandaloneToolProcess.setJavaHome(new File(dir, 'no-java').path)
        err.reset()
        exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().last().contains('names no Java: there is no bin/java in it')
        !ran.exists()
    }

    void "the help of a standalone tool says how it runs, and the document for a tool has its target"() {
        given:
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'tool')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('A tool of org.example:tool-cli-standalone:1.0, which runs as a process of its own, without Gradle and without the application: ' +
                'it is started by this program, with the class path the build of the project wrote down for it.')
        out.toString().replaceAll(/\s+/, ' ').contains('The Gradle task runTool of the project runs it as well, without the application: run-tool tool')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        exit == 0
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }.collectEntries { [(it.name): [it.target, it.startsApplication]] } ==
                ['build-tool': ['application', false], tool: ['standalone', false]]
        !ran.exists()
    }

    void "the numbers of the file are those of what writes it"() {
        expect:
        StandaloneToolsFile.LOCATION == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.LOCATION
        StandaloneToolsFile.TASK == org.apache.grails.gradle.model.cli.ToolBuildNames.STANDALONE_TOOLS_TASK
        StandaloneToolsFile.MAX_BYTES == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_BYTES
        StandaloneToolsFile.MAX_ENTRIES == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_ENTRIES
        StandaloneToolsFile.MAX_PATH == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_PATH_LENGTH
        StandaloneToolsFile.MAX_SOURCES == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_SOURCES
        StandaloneToolsFile.MAX_WALKED == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_WALKED
        StandaloneToolsFile.MAX_WALK_DEPTH == org.apache.grails.gradle.model.cli.StandaloneToolsDocument.MAX_WALK_DEPTH
    }

    void "what the build writes for the tools is what is read here, and the two take the same sources for changed"() {
        given: 'the file as the class of the build writes it, for the catalog as the class of the build writes it'
        String catalog = ProjectToolsSpec.doc([TOOL, LOCAL, BUILD_TOOL])
        listed([TOOL, LOCAL, BUILD_TOOL])
        Map<String, String> tools = org.apache.grails.gradle.model.cli.StandaloneToolsDocument.toolsOf(catalog.getBytes(StandardCharsets.UTF_8))
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        file.text = org.apache.grails.gradle.model.cli.StandaloneToolsDocument.write(
                tools.keySet().toList(), [classes.path, library.path], [new File(dir, 'src/standalone').path], 21, null)

        expect:
        tools == [local: 'application', tool: 'dependencies']

        when:
        int exit = ForgeCli.execute(beanContext, 'local')

        then:
        exit == 0
        line('classpath=') == [classes.path, library.path].join(File.pathSeparator)
        !ran.exists()

        when: 'a source of the standalone tools is changed after the file'
        older(StandaloneToolsFile.LOCATION, 'src/standalone/groovy/LocalTool.groovy')

        then: 'the class of the build takes the same directory for changed'
        org.apache.grails.gradle.model.cli.StandaloneToolsDocument.changedSince([new File(dir, 'src/standalone').path], Files.getLastModifiedTime(file.toPath())) ==
                new File(dir, 'src/standalone').path
    }

    private String line(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        return lines[0].substring(start.length())
    }

    // the catalog of the tools, as the build writes it
    private void listed(List<Map> tools) {
        File file = new File(dir, ToolCatalogFile.LOCATION)
        file.parentFile.mkdirs()
        file.bytes = ProjectToolsSpec.doc(tools).getBytes(StandardCharsets.UTF_8)
    }

    // the file of the class path, as the build writes it: the output of src/standalone before what the tools depend on, and src/standalone as its sources
    void written(Map<String, String> tools, List<String> dependencies = [library.path], int java = 21) {
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        file.parentFile.mkdirs()
        Files.deleteIfExists(file.toPath())
        file.text = JsonOutput.toJson([format: 1, tools: tools.keySet().toList(), classpath: [classes.path] + dependencies,
                                       sources: [new File(dir, 'src/standalone').path], java: java]) + '\n'
    }

    // makes one file of the project older than another; what the file of the class path is compared with, the
    // sources of src/standalone, is older than both, but for the newer file and the directories above it
    void older(String older, String newer) {
        File first = new File(dir, older)
        File second = new File(dir, newer)
        long now = System.currentTimeMillis()
        new File(dir, 'src/standalone').eachFileRecurse { File each -> assert each.setLastModified(now - 30_000) }
        assert new File(dir, 'src/standalone').setLastModified(now - 30_000)
        assert first.setLastModified(now - 20_000)
        for (File each = second; each != dir; each = each.parentFile) {
            assert each.setLastModified(now - 10_000)
        }
    }

    // a new file of the project, written after the file of the class path
    void created(String path) {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.text = 'class Created { }\n'
        older(StandaloneToolsFile.LOCATION, path)
    }

    // a new file of the project, newer than everything the build wrote
    void added(String path, String text = 'class Added { }\n') {
        File file = new File(dir, path)
        file.parentFile.mkdirs()
        file.text = text
        long now = System.currentTimeMillis()
        for (File each = file; each != dir; each = each.parentFile) {
            assert each.setLastModified(now + 2_000)
        }
    }

    void moveLibraryAway() {
        assert library.renameTo(new File(dir, 'library-away'))
        // the build puts it back
        new File(dir, 'when-built.sh') << 'rmdir "$here/lib/library"\nmv "$here/library-away" "$here/lib/library"\n'
    }

    void linked() {
        File file = new File(dir, StandaloneToolsFile.LOCATION)
        File real = new File(dir, 'elsewhere.json')
        real.bytes = file.bytes
        file.delete()
        Files.createSymbolicLink(file.toPath(), real.toPath())
    }

    void notOwned(String name) {
        ConsoleProject.setOwnedByUser({ Path path -> path.getFileName().toString() != name } as Predicate<Path>)
    }

    void writableByEverybody(File directory) {
        Files.setPosixFilePermissions(directory.toPath(), PosixFilePermissions.fromString('rwxrwxrwx'))
    }

    // a class path entry that is in none of the places an entry can be in
    File outside() {
        StandaloneToolsFile.setPlaces([])
        away = Files.createTempDirectory('grailsoutside').toFile().canonicalFile
        File outside = new File(away, 'library')
        assert library.renameTo(outside) || outside.mkdirs()
        if (!new File(outside, 'org').exists()) {
            File source = new File(dir, 'build/StandaloneToolRunner.java')
            assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', outside.path, source.path) == 0
        }
        return outside
    }
}
