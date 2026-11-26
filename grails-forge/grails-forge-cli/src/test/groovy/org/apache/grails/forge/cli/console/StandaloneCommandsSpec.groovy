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
 * A command the build of a project lists with the target {@code standalone} is started by the CLI
 * as a process of its own, with the class path the build wrote down for it, and without Gradle: the
 * Gradle wrapper of the project here is a script that writes down what it was called with, and is
 * called only where the specification says so. The class the process starts is a stand-in that says
 * what it was started with; the real one is specified where it is, in {@code grails-core}.
 */
@IgnoreIf({ os.windows })
class StandaloneCommandsSpec extends CommandSpec implements CommandFixture {

    static final Map TOOL = [name: 'tool', aliases: [], description: 'Renders a file', origin: 'org.example:tool-cli:1.0', target: 'standalone', registration: 'descriptor']
    static final Map LOCAL = [name: 'local', aliases: [], description: 'Of the application', origin: 'application', target: 'standalone', registration: 'descriptor']
    static final Map BUILD_TOOL = [name: 'build-tool', aliases: [], description: 'Needs the build', origin: 'org.example:tool-cli:1.0', target: 'gradle', registration: 'descriptor']

    static final String RUNNER = '''\
        package org.apache.grails.core.cli;

        public class StandaloneCommandRunner {

            public static void main(String[] args) throws Exception {
                System.out.println("arguments=" + args.length);
                String file = args[0].substring("--grails-args-file=".length());
                String vector = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(file)), "UTF-8");
                System.out.println("vector=" + vector);
                System.out.println("system=" + System.getProperty("grails.env") + " " + System.getProperty("grails.full.stacktrace")
                        + " " + System.getProperty("grails.console.enable.terminal") + " " + System.getProperty("grails.console.enable.interactive")
                        + " " + System.getenv("GRAILS_NON_INTERACTIVE"));
                System.out.println("base=" + System.getProperty("base.dir"));
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
        ran = new File(dir, 'wrapper-ran')
        // the wrapper writes down what it was called with, and then does what a specification has it do
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\nhere="$(dirname "$0")"\nprintf \'%s\\n\' "$@" >> "$here/wrapper-ran"\nif [ -f "$here/when-built.sh" ]; then . "$here/when-built.sh"; fi\n'
        wrapper.setExecutable(true, false)
        classes = new File(dir, 'build/classes/java/main')
        classes.mkdirs()
        library = new File(dir, 'lib/library')
        library.mkdirs()
        File source = new File(dir, 'build/StandaloneCommandRunner.java')
        source.text = RUNNER
        assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', library.path, source.path) == 0
        GradleBuild.setNonInteractive(false)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        ConsoleProject.setOwnedByUser(null)
        ConsoleProject.setSafeDirectories(null)
        StandaloneCommandFile.setPlaces(null)
        StandaloneCommandProcess.setJavaHome(null)
        GradleBuild.setNonInteractive(false)
        // a directory a feature took the permissions of is deleted with the others
        new File(dir, '.grails/cache').setWritable(true, true)
        away?.deleteDir()
    }

    void "a standalone command of a plugin is started as a process with the class path the build wrote down, and Gradle is not run"() {
        given:
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool', 'two words', '--flag=a value', '-x', '--', '$HOME')

        then: 'the command ran with what it was given, each argument as it is, in the directory of the project'
        exit == 0
        line('arguments=') == '1'
        new JsonSlurper().parseText(line('vector=')) == ['tool', 'two words', '--flag=a value', '-x', '--', '$HOME']
        line('system=') == 'development null null null null'
        line('base=') == dir.path
        line('directory=') == dir.path

        and: 'with the class path of a command of a plugin, which has nothing of the application'
        line('classpath=') == library.path

        and: 'one line said what runs and how, and nothing ran the build'
        err.toString().readLines() == ['| Running the command tool of org.example:tool-cli:1.0, as a process of its own, without Gradle']
        !ran.exists()

        and: 'nothing of the run is left behind'
        new File(dir, '.grails/run').list().toList() - ['.gitignore'] == []
    }

    void "a standalone command of the application is started with the classes of the application before what it depends on"() {
        given:
        listed([LOCAL])
        written([local: 'application'])

        when:
        int exit = ForgeCli.execute(beanContext, 'local')

        then:
        exit == 0
        line('classpath=') == [classes.path, library.path].join(File.pathSeparator)
        err.toString().readLines() == ['| Running the command local of the application, as a process of its own, without Gradle']
        !ran.exists()
    }

    @Unroll
    void "a standalone command that ends with #code ends the command line with 1, after one line"() {
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
    void "the build runs once, and then the command without it, when #what"() {
        given: 'a build that writes the two files as the tasks do'
        listed([TOOL, LOCAL])
        written([tool: 'dependencies', local: 'application'])
        File catalog = new File(dir, ApplicationCommandFile.LOCATION)
        File file = new File(dir, StandaloneCommandFile.LOCATION)
        new File(dir, 'catalog.json').bytes = catalog.bytes
        new File(dir, 'standalone.json').bytes = file.bytes
        new File(dir, 'when-built.sh').text = 'mkdir -p "$here/lib/library"\ncp "$here/catalog.json" "$here/.grails/cache/command-catalog.json"\n' +
                'cp "$here/standalone.json" "$here/.grails/cache/standalone-commands.json"\n'
        change.call(this)

        when:
        int exit = ForgeCli.execute(beanContext, command, 'argument')

        then: 'one line says why, the build ran the task that writes the file, and then the command ran'
        exit == 0
        err.toString().readLines()[0] == "| Building the project before ${command} is run without Gradle, with the Gradle task grailsStandaloneCommands: ${reason}"
        err.toString().readLines().last() == "| Running the command ${command} of ${command == 'local' ? 'the application' : 'org.example:tool-cli:1.0'}, as a process of its own, without Gradle"
        ran.readLines().count { String argument -> argument.endsWith('grailsStandaloneCommands') } == 1
        !ran.readLines().any { String argument -> argument.endsWith('runCommand') }
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
        what                                                      | command | reason                                                                                         | change
        'the file is not there'                                   | 'tool'  | 'there is no .grails/cache/standalone-commands.json'                                           | { StandaloneCommandsSpec spec -> new File(spec.dir, StandaloneCommandFile.LOCATION).delete() }
        'the file is older than the list of the commands'         | 'tool'  | '.grails/cache/command-catalog.json is newer than .grails/cache/standalone-commands.json'      | { StandaloneCommandsSpec spec -> spec.older(StandaloneCommandFile.LOCATION, ApplicationCommandFile.LOCATION) }
        'the build file is newer than the list of the commands'   | 'tool'  | 'build.gradle is newer than .grails/cache/command-catalog.json'                                | { StandaloneCommandsSpec spec -> new File(spec.dir, 'build.gradle').text = '// changed\n'; spec.older(ApplicationCommandFile.LOCATION, 'build.gradle'); spec.older(StandaloneCommandFile.LOCATION, 'build.gradle') }
        'an entry of the class path is no longer there'           | 'tool'  | 'lib/library of the class path is no longer there'                                             | { StandaloneCommandsSpec spec -> spec.moveLibraryAway() }
        'a source of the application is newer than the file'      | 'local' | 'src/main/groovy was changed after .grails/cache/standalone-commands.json was written'         | { StandaloneCommandsSpec spec -> spec.older(StandaloneCommandFile.LOCATION, 'src/main/groovy/Thing.groovy') }
        'the file is not the document the build writes'           | 'tool'  | '.grails/cache/standalone-commands.json is not the document of format 1 the build writes'      | { StandaloneCommandsSpec spec -> new File(spec.dir, StandaloneCommandFile.LOCATION).text = '{"format":2}\n' }
    }

    void "a source of the application that is newer than the file does not have the build run for a command of a plugin"() {
        given:
        listed([TOOL, LOCAL])
        written([tool: 'dependencies', local: 'application'])
        older(StandaloneCommandFile.LOCATION, 'src/main/groovy/Thing.groovy')

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 0
        !ran.exists()
    }

    void "a build that leaves the file as it was, and one that fails, end the command without running it"() {
        given: 'a build that writes nothing'
        listed([TOOL])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        ran.readLines().count { String argument -> argument.endsWith('grailsStandaloneCommands') } == 1
        out.toString().isEmpty()
        err.toString().readLines().last().endsWith('The build of the project did not write down a class path for the command tool: there is no .grails/cache/standalone-commands.json. ' +
                'The command was not run; the build runs it with: grails-forge-cli run-command tool')

        when: 'a build that fails'
        new File(dir, 'when-built.sh').text = 'exit 3\n'
        ran.delete()
        err.reset()
        exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().last().endsWith('The project could not be built, so the command tool was not run')
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
        said.contains('The command tool is not started as a process of its own: ')
        said.contains(reason)
        said.endsWith('The command was not run; the build runs it with: grails-forge-cli run-command tool')

        where:
        what                                                   | reason                                                                                   | change
        'the file is a link'                                   | '.grails/cache/standalone-commands.json is, or is behind, a link or is not a file'       | { StandaloneCommandsSpec spec -> spec.linked() }
        'the file is not the user\'s own'                      | 'standalone-commands.json is not owned by the user who runs the command'                 | { StandaloneCommandsSpec spec -> spec.notOwned('standalone-commands.json') }
        'the list of the commands is not the user\'s own'      | 'command-catalog.json is not owned by the user who runs the command'                     | { StandaloneCommandsSpec spec -> spec.notOwned('command-catalog.json') }
        'everybody can write to the directory of the file'     | '.grails/cache can be written to by everybody'                                           | { StandaloneCommandsSpec spec -> spec.writableByEverybody(new File(spec.dir, '.grails/cache')) }
        'an entry of the class path is outside the build'      | 'is not within the build of the project, the Gradle user home or the local Maven repository' | { StandaloneCommandsSpec spec -> spec.written([tool: 'dependencies'], [spec.outside().path]) }
        'an entry of the class path is not a whole path'       | 'is not a whole path that names its file itself'                                         | { StandaloneCommandsSpec spec -> spec.written([tool: 'dependencies'], ['lib/library']) }
        'an entry of the class path names its file by a way up' | 'is not a whole path that names its file itself'                                        | { StandaloneCommandsSpec spec -> spec.written([tool: 'dependencies'], [new File(spec.dir, 'lib/../lib/library').path]) }
        'everybody can write to an entry of the class path'    | 'lib/library can be written to by everybody'                                             | { StandaloneCommandsSpec spec -> spec.writableByEverybody(spec.library) }
        'everybody can write to a directory above an entry'    | 'lib can be written to by everybody'                                                     | { StandaloneCommandsSpec spec -> spec.writableByEverybody(new File(spec.dir, 'lib')) }
        'an entry of the class path is another user\'s'        | 'library is owned neither by the user who runs the command nor by root'                  | { StandaloneCommandsSpec spec -> spec.notOwned('library') }
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
        line('classpath=') == outside.path
        !ran.exists()
    }

    void "a class path is taken for no command the list does not say is a standalone command"() {
        given: 'a file that names a command the build runs'
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies', 'build-tool': 'dependencies'])

        when:
        ForgeCli.execute(beanContext, 'build-tool')

        then: 'it is run by the build, as the list says'
        out.toString().isEmpty()
        ran.readLines().any { String argument -> argument.endsWith('runCommand') }
        err.toString().readLines()[0] == '| Running the command build-tool of org.example:tool-cli:1.0, with the Gradle task runCommand'
    }

    void "a command the build says it runs itself is run with Gradle, after a line that says why"() {
        given:
        listed([TOOL])
        File file = new File(dir, StandaloneCommandFile.LOCATION)
        file.text = JsonOutput.toJson([format: 1, commands: [tool: 'dependencies'], application: [], dependencies: [], sources: [], java: 21,
                                       throughGradle: 'their class path has the output of another project of the build (:lib), which only the build keeps current']) + '\n'

        when:
        ForgeCli.execute(beanContext, 'tool', 'argument')

        then:
        out.toString().isEmpty()
        err.toString().readLines()[0..1] == [
                '| The command tool is run with Gradle: their class path has the output of another project of the build (:lib), which only the build keeps current',
                '| Running the command tool of org.example:tool-cli:1.0, with the Gradle task runCommand']
        ran.readLines().any { String argument -> argument.endsWith('runCommand') }
    }

    void "run-command runs a standalone command with Gradle, as it runs every command"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        ForgeCli.execute(beanContext, 'run-command', '-Dgrails.env=production', 'tool', 'argument')

        then:
        out.toString().isEmpty()
        err.toString().readLines()[0] == '| Running the command tool of org.example:tool-cli:1.0, with the Gradle task runCommand'
        ran.readLines().any { String argument -> argument.endsWith('runCommand') }
        ran.readLines().contains('-Dgrails.env=production')
    }

    void "the environment after the name of a standalone command is an argument of the command, and a warning says so"() {
        given:
        listed([TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'tool', '-Dgrails.env=production')

        then:
        exit == 0
        line('system=').startsWith('development ')
        new JsonSlurper().parseText(line('vector=')) == ['tool', '-Dgrails.env=production']
        err.toString().readLines().any { String line -> line.startsWith('| Warning -Dgrails.env=... after the name of tool is given to the command as an argument') }
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
        err.toString().readLines().last().endsWith('The command was not run; the build runs it with: grails-forge-cli run-command tool')
        !ran.exists()

        when:
        written([tool: 'dependencies'])
        StandaloneCommandProcess.setJavaHome(new File(dir, 'no-java').path)
        err.reset()
        exit = ForgeCli.execute(beanContext, 'tool')

        then:
        exit == 1
        out.toString().isEmpty()
        err.toString().readLines().last().contains('names no Java: there is no bin/java in it')
        !ran.exists()
    }

    void "the help of a standalone command says how it runs, and the document for a tool has its target"() {
        given:
        listed([TOOL, BUILD_TOOL])
        written([tool: 'dependencies'])

        when:
        int exit = ForgeCli.execute(beanContext, 'help', 'tool')

        then:
        exit == 0
        out.toString().replaceAll(/\s+/, ' ').contains('A command of org.example:tool-cli:1.0, which runs as a process of its own, without Gradle and without the application: ' +
                'it is started by this program, with the class path the build of the project wrote down for it.')

        when:
        out.reset()
        exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString().readLines().last())

        then:
        exit == 0
        ((List<Map>) described.commands).findAll { it.origin != 'built-in' }.collectEntries { [(it.name): it.target] } == ['build-tool': 'gradle', tool: 'standalone']
        !ran.exists()
    }

    void "the numbers of the file are those of what writes it"() {
        expect:
        StandaloneCommandFile.LOCATION == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.LOCATION
        StandaloneCommandFile.MAX_BYTES == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_BYTES
        StandaloneCommandFile.MAX_ENTRIES == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_ENTRIES
        StandaloneCommandFile.MAX_PATH == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_PATH_LENGTH
        StandaloneCommandFile.MAX_SOURCES == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_SOURCES
        StandaloneCommandFile.MAX_WALKED == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_WALKED
        StandaloneCommandFile.MAX_WALK_DEPTH == org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.MAX_WALK_DEPTH
    }

    void "what the build writes for the commands is what is read here"() {
        given: 'the file as the class of the build writes it, for the list as the class of the build writes it'
        String catalog = ApplicationCommandsSpec.doc([TOOL, LOCAL, BUILD_TOOL])
        listed([TOOL, LOCAL, BUILD_TOOL])
        Map<String, String> commands = org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.commandsOf(catalog.getBytes(StandardCharsets.UTF_8))
        new File(dir, StandaloneCommandFile.LOCATION).text = org.apache.grails.gradle.model.cli.StandaloneCommandsDocument.write(
                commands, [classes.path], [library.path], [new File(dir, 'src/main/groovy').path], 21, null)

        expect:
        commands == [local: 'application', tool: 'dependencies']

        when:
        int exit = ForgeCli.execute(beanContext, 'local')

        then:
        exit == 0
        line('classpath=') == [classes.path, library.path].join(File.pathSeparator)
        !ran.exists()
    }

    private String line(String start) {
        List<String> lines = out.toString().readLines().findAll { String line -> line.startsWith(start) }
        assert lines.size() == 1
        return lines[0].substring(start.length())
    }

    // the list of the commands, as the build writes it
    private void listed(List<Map> commands) {
        File file = new File(dir, ApplicationCommandFile.LOCATION)
        file.parentFile.mkdirs()
        file.bytes = ApplicationCommandsSpec.doc(commands).getBytes(StandardCharsets.UTF_8)
    }

    // the file of the class paths, as the build writes it after the list
    void written(Map<String, String> commands, List<String> dependencies = [library.path], int java = 21) {
        File file = new File(dir, StandaloneCommandFile.LOCATION)
        file.parentFile.mkdirs()
        Files.deleteIfExists(file.toPath())
        boolean ofApplication = commands.containsValue('application')
        file.text = JsonOutput.toJson([format: 1, commands: commands, application: ofApplication ? [classes.path] : [], dependencies: dependencies,
                                       sources: ofApplication ? [new File(dir, 'src/main/groovy').path] : [], java: java]) + '\n'
    }

    // makes one file of the project older than another
    void older(String older, String newer) {
        File first = new File(dir, older)
        File second = new File(dir, newer)
        long now = System.currentTimeMillis()
        assert first.setLastModified(now - 20_000)
        assert second.setLastModified(now - 10_000)
        // what is compared with a file that was made older stays older than it
        if (older == StandaloneCommandFile.LOCATION && newer != ApplicationCommandFile.LOCATION) {
            assert new File(dir, ApplicationCommandFile.LOCATION).setLastModified(now - 30_000)
        }
    }

    void moveLibraryAway() {
        assert library.renameTo(new File(dir, 'library-away'))
        // the build puts it back
        new File(dir, 'when-built.sh') << 'rmdir "$here/lib/library"\nmv "$here/library-away" "$here/lib/library"\n'
    }

    void linked() {
        File file = new File(dir, StandaloneCommandFile.LOCATION)
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
        StandaloneCommandFile.setPlaces([])
        away = Files.createTempDirectory('grailsoutside').toFile().canonicalFile
        File outside = new File(away, 'library')
        assert library.renameTo(outside) || outside.mkdirs()
        if (!new File(outside, 'org').exists()) {
            File source = new File(dir, 'build/StandaloneCommandRunner.java')
            assert ToolProvider.getSystemJavaCompiler().run(null, null, null, '-d', outside.path, source.path) == 0
        }
        return outside
    }
}
