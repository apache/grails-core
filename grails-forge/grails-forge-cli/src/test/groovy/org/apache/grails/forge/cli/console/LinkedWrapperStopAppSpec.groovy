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

import java.nio.file.Files
import java.nio.file.Path
import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.grails.forge.application.ApplicationType
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * {@code stop-app} in a project whose {@code gradlew} is a link to {@code tools/gradlew}. The script
 * finds the jar it runs from where its file really is, so the Gradle that {@code run-app} started has
 * {@code tools/gradle/wrapper/gradle-wrapper.jar} in its command line, and not a jar beside the link:
 * that jar is the one a recorded process is known by to be the wrapper of this project.
 */
@IgnoreIf({ os.windows })
class LinkedWrapperStopAppSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    List<Long> started = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        File marker = new File(dir, 'server.pid')
        if (marker.isFile() && marker.text.trim() ==~ /[0-9]+/) {
            started.add(marker.text.trim() as long)
        }
        // whatever a feature started and did not see end is ended here, and the feature has then failed
        List<Long> left = started.findAll { Long pid -> alive(pid) }
        left.each { Long pid ->
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
        assert left.isEmpty()
    }

    @Unroll
    void "with a wrapper that is a link, a recorded process is #taken for the Gradle of the project when its command line has #what"() {
        given: 'a wrapper that is a link to a script in tools, with a jar beside the script and another beside the link'
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'tools').mkdirs()
        new File(dir, 'tools/gradlew').text = '#!/bin/sh\n'
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), Path.of('tools/gradlew'))
        ['gradle/wrapper', 'tools/gradle/wrapper'].each { String wrapper ->
            new File(dir, wrapper).mkdirs()
            new File(dir, "${wrapper}/gradle-wrapper.jar").text = wrapper
        }
        // a shell that waits for a line that does not come, and so stays the process with these words
        List<String> line = ['sh', '-c', 'read line', 'decoy'] + words.collect { String word -> word.replace('DIR', dir.canonicalPath) }
        Process other = new ProcessBuilder(line).start()
        started.add(other.pid())
        new File(dir, '.grails/run').mkdirs()
        new File(dir, '.grails/run/app.json').text = JsonOutput.toJson([format: 1, port: 0, detached: true, pid: other.pid(),
                                                                         started: other.info().startInstant().get().toEpochMilli()])

        when:
        int exit = ForgeCli.execute(beanContext, 'stop-app')

        then:
        exit == (stopped ? 0 : 1)
        other.alive == !stopped
        new File(dir, '.grails/run/app.json').exists() == !stopped
        stopped || (errors().size() == 1 && errors()[0].contains('its command line is not that of the Gradle wrapper of this project running bootRun'))

        cleanup:
        other?.destroyForcibly()
        other?.waitFor()

        where:
        what                                                                     | words                                                                                                           | stopped
        'the link and the task'                                                  | ['DIR/gradlew', 'bootRun']                                                                                      | true
        'the script the link leads to and the task'                              | ['DIR/tools/gradlew', 'bootRun']                                                                                | true
        'the jar beside the script as the jar of Java, and the task'             | ['-jar', 'DIR/tools/gradle/wrapper/gradle-wrapper.jar', 'bootRun']                                              | true
        'the jar beside the script as a class path, its main class and the task' | ['-classpath', 'DIR/tools/gradle/wrapper/gradle-wrapper.jar', 'org.gradle.wrapper.GradleWrapperMain', 'bootRun'] | true
        'the jar beside the link, which the script does not run'                 | ['-jar', 'DIR/gradle/wrapper/gradle-wrapper.jar', 'bootRun']                                                    | false
        'the jar beside the script and another main class'                       | ['-classpath', 'DIR/tools/gradle/wrapper/gradle-wrapper.jar', 'Other', 'bootRun']                               | false
        taken = stopped ? 'taken' : 'not taken'
    }

    void "run-app and stop-app work with a Gradle wrapper that is a link to a script elsewhere in the project"() {
        given: 'the wrapper of a generated application in tools, a link to its script where the wrapper was, and a build that needs nothing of Grails'
        generateProject(ApplicationType.WEB)
        dir.eachFile { File file ->
            if (!(file.name in ['gradlew', 'gradle'])) {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        new File(dir, 'gradle').eachFile { File file ->
            if (file.name != 'wrapper') {
                file.directory ? file.deleteDir() : file.delete()
            }
        }
        File tools = new File(dir, 'tools')
        tools.mkdirs()
        Files.move(new File(dir, 'gradlew').toPath(), new File(tools, 'gradlew').toPath())
        Files.move(new File(dir, 'gradle').toPath(), new File(tools, 'gradle').toPath())
        Files.createSymbolicLink(new File(dir, 'gradlew').toPath(), Path.of('tools/gradlew'))
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        new File(dir, 'gradle.properties').text = 'org.gradle.daemon=false\n'
        new File(dir, 'build.gradle').text = LifecycleCommandsSpec.BUILD
        new File(dir, 'grails-app').mkdirs()
        int port = new ServerSocket(0).withCloseable { ServerSocket socket -> socket.localPort }

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', port as String, '--ready-timeout', '600')
        File marker = new File(dir, 'server.pid')
        SpecWaits.eventually {
            assert marker.isFile() && marker.text.endsWith('\n')
        }
        long building = marker.text.trim() as long
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map
        started.addAll([building, record.pid as long])

        then:
        exit == 0
        errors() == []
        ApplicationPort.isOpen(port)
        alive(building)

        when: 'the record is believed: the process is the Gradle wrapper of this project running bootRun'
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 0
        errors() == []
        !alive(building) && !alive(record.pid as long)
        !ApplicationPort.isOpen(port)
        !new File(dir, '.grails/run/app.json').exists()
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map { ProcessHandle handle -> handle.alive }.orElse(false)
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}
