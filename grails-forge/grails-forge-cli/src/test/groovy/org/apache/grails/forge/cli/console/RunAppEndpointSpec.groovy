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

import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * The port and the address {@code run-app} waits on are those the application is told, by the
 * arguments the build is given: what follows {@code --} is given to Gradle after the options of the
 * command, and a system property there is the one Gradle and the application have, whichever form
 * Gradle reads it in. So the command waits where the application answers, and records that port for
 * {@code stop-app}.
 *
 * <p>The first features run the Gradle wrapper of the build of the specification around a build that
 * needs nothing of Grails, whose {@code bootRun} answers on the port of {@code grails.server.port} as
 * Gradle read it, without a daemon: Gradle itself reads the arguments. The others have a script in the
 * place of the wrapper, as in {@link LifecycleCommandsSpec}.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class RunAppEndpointSpec extends CommandSpec {

    @Shared
    IsolatedGradle gradle = new IsolatedGradle()

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
        File server = new File(dir, 'server.pid')
        if (server.isFile() && server.text.trim() ==~ /[0-9]+/) {
            started.add(server.text.trim() as long)
        }
        started.each { Long pid ->
            ProcessHandle.of(pid).ifPresent { ProcessHandle handle ->
                handle.descendants().forEach { ProcessHandle each -> each.destroyForcibly() }
                handle.destroyForcibly()
            }
        }
    }

    void cleanupSpec() {
        gradle.close()
    }

    @Unroll
    void "run-app --detach waits on the port Gradle reads from #form after --, and stop-app stops the application there"() {
        given:
        gradleProject()
        int port = freePort()
        int other = freePort()
        List<String> line = (['run-app', '--detach', '--ready-timeout', SpecWaits.boundSeconds as String] + given).collect { String each ->
            each.replace('OTHER', other as String).replace('PORT', port as String)
        }

        when:
        int exit = ForgeCli.execute(beanContext, line as String[])

        then: 'the command waited where the application answers'
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}")
        ApplicationPort.isOpen(port)
        !ApplicationPort.isOpen(other)

        and: 'and recorded that port for stop-app'
        record().port == port

        when:
        out.reset()
        int stopped = ForgeCli.execute(beanContext, 'stop-app')

        then:
        stopped == 0
        errors() == []
        !ApplicationPort.isOpen(port)

        where:
        form                                              | given
        '-Dgrails.server.port=PORT'                        | ['--', '-Dgrails.server.port=PORT']
        '-D grails.server.port=PORT'                       | ['--', '-D', 'grails.server.port=PORT']
        '--system-prop grails.server.port=PORT'            | ['--', '--system-prop', 'grails.server.port=PORT']
        '--system-prop=grails.server.port=PORT'            | ['--', '--system-prop=grails.server.port=PORT']
        '-Dgrails.server.port=PORT, over --port'           | ['--port', 'OTHER', '--', '-Dgrails.server.port=PORT']
        '-Dgrails.server.port=PORT, over the one before --' | ['-Dgrails.server.port=OTHER', '--', '-Dgrails.server.port=PORT']
    }

    void "the environment named after -- is the one whose port of application.yml is waited on"() {
        given:
        scriptedProject()
        int port = freePort()
        // the application, run for the environment test, answers on the port that environment names
        new File(dir, 'port.txt').text = "${port}\n"
        new File(dir, 'grails-app/conf').mkdirs()
        new File(dir, 'grails-app/conf/application.yml').text = "server:\n    port: 1\nenvironments:\n    test:\n        server:\n            port: ${port}\n"

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--ready-timeout', SpecWaits.boundSeconds as String, '--', '-Dgrails.env=test')

        then:
        exit == 0
        errors() == []
        new File(dir, 'invoked.txt').text == '<bootRun><--quiet><-Dgrails.env=development><-Dgrails.env=test>\n'
        record().port == port

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    @Unroll
    void "run-app --detach starts nothing for #what after --, as it does for the same before it"() {
        given:
        scriptedProject()

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--ready-timeout', SpecWaits.boundSeconds as String, '--'] + given) as String[])

        then:
        exit == code
        out.toString().contains(said)
        !new File(dir, 'invoked.txt').exists()
        !new File(dir, '.grails/run/app.json').exists()

        where:
        what                                                  | given                                      | code | said
        '-Dserver.port, which the application is not given'   | ['-Dserver.port=18082']                    | 2    | '-Dserver.port is a system property of the build, which the Grails Gradle plugin does not give to the application'
        'an address the loopback address does not reach'      | ['-Dgrails.server.address=192.0.2.7']      | 1    | 'The application is bound to 192.0.2.7'
        'a port that is not plainly one'                      | ['-Dgrails.server.port=${PORT}']           | 1    | 'The port the application answers on could not be determined: -Dgrails.server.port is given'
        'arguments of the application that name its port'     | ['--args=--server.port=18083']             | 2    | '--args gives the application arguments that name its port or its address'
        'arguments of the application that name its address'  | ['--args', '--server.address=0.0.0.0 --debug'] | 2 | '--args gives the application arguments that name its port or its address'
    }

    void "run-app that stays with the application records the port Gradle reads after --, which stop-app waits on"() {
        given:
        scriptedProject()
        int port = freePort()

        when:
        Thread command = new Thread({ ForgeCli.execute(beanContext, 'run-app', '--', '-Dgrails.server.port=' + port) })
        command.start()
        SpecWaits.eventually {
            assert ApplicationPort.isOpen(port)
            assert new File(dir, '.grails/run/app.json').isFile()
        }

        then:
        record().port == port

        when:
        int stopped = ForgeCli.execute(beanContext, 'stop-app')
        SpecWaits.join(command, 'the end of the thread command')

        then:
        stopped == 0
        !command.alive
        !ApplicationPort.isOpen(port)
    }

    // the Gradle wrapper of the build of this specification, without a daemon: the build is the process stop-app ends
    private void gradleProject() {
        gradle.project(dir, new File(previousUsrDir, '..'))
        new File(dir, 'settings.gradle').text = gradle.settingsOf(false)
        new File(dir, 'gradle.properties').text = gradle.propertiesOf(false)
        new File(dir, 'build.gradle').text = LifecycleCommandsSpec.BUILD
    }

    private void scriptedProject() {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'Server.java').text = LifecycleCommandsSpec.SERVER
        new File(dir, 'mode.txt').text = 'serve\n'
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = "#!/bin/sh\n\"${System.getProperty('java.home')}/bin/java\" \"${dir.canonicalPath}/Server.java\" \"\$@\"\n"
        wrapper.setExecutable(true, false)
    }

    private Map record() {
        Map record = new JsonSlurper().parse(new File(dir, '.grails/run/app.json')) as Map
        started.add(record.pid as long)
        return record
    }

    private static int freePort() {
        return new ServerSocket(0, 1, InetAddress.getLoopbackAddress()).withCloseable { ServerSocket socket -> socket.localPort }
    }

    private List<String> errors() {
        return out.toString().readLines().findAll { String line -> line.contains('| Error') }
    }
}
