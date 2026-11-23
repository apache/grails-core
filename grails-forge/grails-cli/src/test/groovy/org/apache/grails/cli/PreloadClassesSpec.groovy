/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.cli

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import org.apache.grails.forge.cli.StartupPreload
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

/**
 * The lists of the classes the command line loads ahead ({@link StartupPreload}) are files of the
 * sources, so that the jar is the same whoever builds it. This holds them to what the commands load:
 * it runs the jar the distribution has, with the JVM writing down every class it loads, and fails
 * when the lists have drifted from that by more than a few classes, which a computer or a JDK of
 * another kind accounts for.
 *
 * <p>To write the lists anew, after a change of the commands or of a dependency:
 * {@code FORGE_UPDATE_PRELOAD=true ./gradlew :grails-cli:test --tests '*PreloadClassesSpec'}, and
 * review what changed.</p>
 */
class PreloadClassesSpec extends Specification {

    // of the classes a command loads from the jar, how many the lists have to name at least
    static final double COVERED = 0.90d
    // of the classes a list names, how many the commands have to load, and how many the jar has to have, at least
    static final double LOADED = 0.85d
    static final double PRESENT = 0.97d

    @Shared
    File allJar = new File(System.getProperty('grails.cli.all.jar'))
    @Shared
    File sources = new File(System.getProperty('grails.cli.preload.sources'))
    @Shared
    File dir = Files.createTempDirectory('grailspreload').toFile().canonicalFile
    // the classes of the jar that the commands every command line is like loaded, and the ones creating an application loaded besides
    @Shared
    Set<String> ofCommands = new TreeSet<>()
    @Shared
    Set<String> ofGenerator = new TreeSet<>()

    void setupSpec() {
        File application = new File(dir, 'demo')
        Set<String> generating = loaded(dir, 'create-app', 'demo')
        assert new File(application, 'grails-app').directory
        ofCommands.addAll(loaded(dir, '--version'))
        ofCommands.addAll(loaded(dir, '--help'))
        ofCommands.addAll(loaded(application, '--help'))
        ofCommands.addAll(loaded(application, 'help', '--json'))
        ofCommands.addAll(loaded(application, 'create-controller', 'Foo'))
        ofCommands.addAll(loaded(application, 'stats'))
        ofGenerator.addAll(generating - ofCommands)
        if (System.getenv('FORGE_UPDATE_PRELOAD') == 'true') {
            write(file(StartupPreload.CLASSES), ofCommands)
            write(file(StartupPreload.GENERATOR_CLASSES), ofGenerator)
        }
    }

    void cleanupSpec() {
        dir.deleteDir()
    }

    void "the list of #what names what the commands load from the jar, and little else"() {
        given:
        List<String> listed = names(file(resource))
        Set<String> loads = generator ? ofGenerator : ofCommands

        expect: 'most of what is loaded is listed, and most of what is listed is loaded'
        loads.size() > 500
        (loads.intersect(listed).size() / (double) loads.size()) >= COVERED
        (loads.intersect(listed).size() / (double) listed.size()) >= LOADED

        where:
        what                                    | resource                          | generator
        'every command'                         | StartupPreload.CLASSES            | false
        'a command that starts the generator'   | StartupPreload.GENERATOR_CLASSES  | true
    }

    void "a list is in the order of the names, names a class once, and names classes of the jar: #resource"() {
        given:
        List<String> listed = names(file(resource))
        Set<String> entries = new JarFile(allJar).withCloseable { JarFile jar -> jar.entries().collect { it.name } as Set<String> }

        expect:
        listed == listed.toSorted()
        listed == listed.unique(false)
        file(resource).readLines().first().startsWith('# Licensed to the Apache Software Foundation')

        and: 'no class the JVM or a library makes while it runs, whose name is of one run, and none of the JDK'
        listed.findAll { String name -> !(name ==~ /[\p{L}_$][\p{L}\p{N}_$]*(\.[\p{L}_$][\p{L}\p{N}_$]*)*/) } == []
        listed.findAll { String name -> name.contains('$$') || name.contains('Lambda') || name.contains('$Proxy') ||
                name.startsWith('java.') || name.startsWith('javax.') || name.startsWith('jdk.') || name.startsWith('sun.') } == []

        and: 'nearly all of them are in the jar still: one that is not is passed over when the command line starts'
        (listed.count { String name -> entries.contains(name.replace('.', '/') + '.class') } / (double) listed.size()) >= PRESENT

        where:
        resource << [StartupPreload.CLASSES, StartupPreload.GENERATOR_CLASSES]
    }

    // not while the lists are written anew: the jar was made with the ones before
    @IgnoreIf({ env.FORGE_UPDATE_PRELOAD == 'true' })
    void "the two lists have no class in common, and the jar has both"() {
        expect:
        names(file(StartupPreload.CLASSES)).intersect(names(file(StartupPreload.GENERATOR_CLASSES))) == []
        new JarFile(allJar).withCloseable { JarFile jar ->
            [StartupPreload.CLASSES, StartupPreload.GENERATOR_CLASSES].every { String resource ->
                jar.getEntry(resource) != null && jar.getInputStream(jar.getEntry(resource)).getText('UTF-8') == file(resource).getText('UTF-8')
            }
        }
    }

    void "the commands that start the generator are the ones the command line has for creating an application or a plugin"() {
        when:
        Process help = java(dir, [], '--help')
        String text = help.inputStream.getText('UTF-8')
        help.waitFor()
        List<String> creating = text.readLines().collect { String line -> line.trim().split(/\s+/)[0] }
                .findAll { String word -> word ==~ /create-(app|webapp|plugin|web-plugin|rest-api-plugin|restapi)/ }

        then:
        creating as Set<String> == StartupPreload.GENERATOR_COMMANDS as Set<String>
    }

    // Loading the classes of the generator ahead takes about a tenth off a command that starts it, and only the
    // processor time of a second thread from one that does not: what the command line says it loaded ahead is held
    // to what it says the command started.
    @Requires({ Runtime.runtime.availableProcessors() > 1 })
    void "a command loads the classes of the generator ahead where it starts the generator, and not where it does not: #arguments"() {
        given:
        File directory = recorded ? new File(dir, 'demo') : unrecorded()
        File err = File.createTempFile('err', '.txt', dir)

        when:
        Process process = java(directory, [], arguments as String[], err, [GRAILS_CLI_DIAGNOSTICS: 'true'])
        process.inputStream.getText('UTF-8')
        process.waitFor()
        List<String> said = err.readLines('UTF-8').findAll { String line -> line.startsWith('| Diagnostic ') }
        String preload = said.find { String line -> line.startsWith('| Diagnostic preload: ') }
        String ran = said.find { String line -> line.startsWith('| Diagnostic project generator: ') }

        then: 'the classes of the generator are loaded ahead exactly where the generator runs'
        preload ==~ /\| Diagnostic preload: started: the classes every command loads( and those of the project generator)?, (still loading|done)/
        ran != null
        preload.contains(' and those of the project generator') == generator
        ran.startsWith('| Diagnostic project generator: running') == generator

        where:
        arguments                                   | recorded | generator
        ['feature', 'list']                         | true     | true
        ['feature', 'add', 'cache', '--dry-run']    | true     | true
        ['project', 'regenerate', '--diff']         | true     | true
        ['project', 'migrate', '--dry-run']         | false    | true
        ['project', 'show']                         | true     | false
        ['help', 'feature', 'add']                  | true     | false
        ['--version']                               | true     | false
        ['stats']                                   | true     | false
    }

    void "a command writes what it writes without the classes loaded ahead, and the same with them"() {
        when:
        List<String> with = output(arguments, [])
        List<String> without = output(arguments, ['-Dgrails.cli.preload=false'])

        then:
        with == without
        (with[0] + with[2]).readLines().size() > 1

        where:
        arguments << [['--version'], ['--help'], ['help', 'create-app'], ['no-such-command']]
    }

    // what a command writes to its two outputs, and how it ends
    private List<String> output(List<String> arguments, List<String> options) {
        File err = File.createTempFile('err', '.txt', dir)
        Process process = java(dir, options, arguments as String[], err)
        String out = process.inputStream.getText('UTF-8')
        return [out, String.valueOf(process.waitFor()), err.getText('UTF-8')]
    }

    // the application of the specification without its record, which project migrate writes
    private File unrecorded() {
        File copy = new File(dir, 'unrecorded')
        if (!copy.directory) {
            Path from = new File(dir, 'demo').toPath()
            Files.walk(from).withCloseable { stream ->
                stream.filter { Path each -> !from.relativize(each).toString().startsWith('.grails') }.forEach { Path each ->
                    Path to = copy.toPath().resolve(from.relativize(each).toString())
                    if (Files.isDirectory(each)) {
                        Files.createDirectories(to)
                    } else {
                        Files.copy(each, to)
                    }
                }
            }
        }
        return copy
    }

    private File file(String resource) {
        return new File(sources, resource)
    }

    private static List<String> names(File list) {
        return list.readLines('UTF-8').collect { String line -> line.trim() }.findAll { String line -> line && !line.startsWith('#') }
    }

    private static void write(File list, Set<String> names) {
        List<String> header = list.readLines('UTF-8').takeWhile { String line -> line.startsWith('#') }
        list.setText((header + names.toSorted()).join('\n') + '\n', 'UTF-8')
    }

    // The classes a command line loads from the jar. The command line is run without the classes loaded ahead:
    // what it loads is asked, not what the lists make it load.
    private Set<String> loaded(File directory, String... arguments) {
        // named without a directory, in the one the command runs in: the option of the JVM takes a colon for the end of the name
        File log = new File(directory, "classes-loaded-${System.nanoTime()}.log")
        Process process = java(directory, ["-Xlog:class+load=info:file=${log.name}".toString(), '-Dgrails.cli.preload=false'], arguments)
        process.inputStream.getText('UTF-8')
        assert process.waitFor() == 0
        String jar = allJar.name
        Set<String> names = new TreeSet<>()
        log.eachLine('UTF-8') { String line ->
            // [0.123s][info][class,load] org.example.Name source: file:/.../the.jar
            int source = line.indexOf(' source: ')
            int name = line.lastIndexOf(' ', source - 1)
            if (source < 0 || name < 0 || !line.substring(source).contains(jar)) {
                return
            }
            String loadedClass = line.substring(name + 1, source)
            if (!loadedClass.contains('/') && !loadedClass.contains('$$') && !loadedClass.contains('Lambda') && !loadedClass.contains('$Proxy')) {
                names.add(loadedClass)
            }
        }
        log.delete()
        return names
    }

    private Process java(File directory, List<String> options, String[] arguments, File err = null, Map<String, String> environment = [:]) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path, '--enable-native-access=ALL-UNNAMED',
                                "-Duser.home=${new File(dir, 'home').path}".toString()] + options +
                ['-cp', allJar.path, 'org.apache.grails.cli.GrailsCli'] + arguments.toList()
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory)
        builder.redirectError(err == null ? ProcessBuilder.Redirect.DISCARD : ProcessBuilder.Redirect.to(err))
        builder.redirectInput(ProcessBuilder.Redirect.from(new File(File.separatorChar == ('\\' as char) ? 'NUL' : '/dev/null')))
        builder.environment().remove('COLORTERM')
        builder.environment().put('GRAILS_FORGE_CATALOG', 'none')
        builder.environment().putAll(environment)
        return builder.start()
    }
}
