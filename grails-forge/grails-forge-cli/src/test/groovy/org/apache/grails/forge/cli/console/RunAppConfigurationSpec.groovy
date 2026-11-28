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
import java.util.function.Function
import groovy.json.JsonSlurper
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import org.grails.forge.cli.SpecWaits
import spock.lang.IgnoreIf
import spock.lang.Timeout
import spock.lang.Unroll

/**
 * What {@code run-app --detach} says when {@code grails-app/conf/application.yml} is a file it cannot
 * read the port or the address of: what is wrong with the file and where, in the words of the command
 * and with nothing of the file, and what to give for what is not known. The address is read from the
 * file also when {@code --port} is given, since the command waits on the loopback address and does not
 * start an application that is bound to another one; an option that was given is never asked for.
 *
 * <p>The project has a script in the place of the Gradle wrapper, as in {@link LifecycleCommandsSpec},
 * which writes down that it was started and, for the one feature that starts the application, answers
 * on the port it is given.</p>
 */
@IgnoreIf({ os.windows })
@Timeout(600)
class RunAppConfigurationSpec extends CommandSpec {

    static final String CONFIGURATION = 'grails-app/conf/application.yml'
    // a key indented by four spaces, and its sibling by two
    static final String MISINDENTED = 'info:\n    app: shop\n  build: 1\n'
    static final String STARTS = 'Nothing was started; '
    static final String FOREGROUND = 'stay with the application with --foreground'

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    List<Long> started = []

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(out, true))
        // what the environment of the computer the specification runs on names is not what the features are about
        RunAppCommand.setEnvironmentVariables({ String name -> null } as Function<String, String>)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
        RunAppCommand.setEnvironmentVariables(null)
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

    @Unroll
    void "run-app --detach #options starts nothing when application.yml #what, and says where and what to give"() {
        given:
        scriptedProject()
        File file = new File(dir, CONFIGURATION)
        file.bytes = configuration instanceof byte[] ? (byte[]) configuration : configuration.toString().getBytes(StandardCharsets.UTF_8)

        when:
        int exit = ForgeCli.execute(beanContext, (['run-app', '--detach', '--ready-timeout', SpecWaits.boundSeconds as String] + options) as String[])

        then:
        exit == 1
        errors() == ['| Error ' + said]
        out.toString().readLines().size() == 1
        !new File(dir, 'invoked.txt').exists()

        where:
        what                                     | configuration                                          | options              | said
        'is not valid YAML'                      | MISINDENTED                                            | []                   | both("${CONFIGURATION} is not valid YAML at line 3, column 3")
        'is not valid YAML'                      | MISINDENTED                                            | ['--port', '18181']  | address("${CONFIGURATION} is not valid YAML at line 3, column 3")
        'has a key twice in one map'             | 'server:\n  port: 1\nserver:\n  port: 2\n'             | []                   | both("${CONFIGURATION} has a key twice in one map at line 3, column 1, which YAML does not allow")
        'has a key twice in one map'             | 'server:\n  port: 1\nserver:\n  port: 2\n'             | ['--port', '18181']  | address("${CONFIGURATION} has a key twice in one map at line 3, column 1, which YAML does not allow")
        'nests deeper than the command reads'    | 'x: ' + '[' * 40 + ']' * 40 + '\nserver:\n  port: 1\n' | []                   | both("${CONFIGURATION} nests lists and maps deeper than 32 levels, more than the command reads")
        'nests deeper than the command reads'    | 'x: ' + '[' * 40 + ']' * 40 + '\n'                     | ['--port', '18181']  | address("${CONFIGURATION} nests lists and maps deeper than 32 levels, more than the command reads")
        'has more aliases than the command reads' | 'a: &a [1]\nb: [' + (['*a'] * 21).join(', ') + ']\n'  | []                   | both("${CONFIGURATION} refers to a list or a map by an alias more than 20 times, more than the command reads")
        'is larger than the command reads'       | 'x: 1\n' * ((1024 * 1024).intdiv(5) + 1)              | ['--port', '18181']  | address("${CONFIGURATION} is larger than 1048576 bytes, and is not read")
        'has a character YAML does not allow'    | 'a: 1\r\nb: 2\r\nserver: [\u0000'                      | []                   | both("${CONFIGURATION} is not valid YAML at line 3, column 10, where it has a character YAML does not allow")
        'has a tag the command does not know'    | 'server:\n  port: !port 1\n'                           | []                   | both("${CONFIGURATION} has a value at line 2, column 9 that the command cannot read as YAML data, such as one with a tag it does not know")
        'has a key that holds itself'            | '? &a [*a]\n: 1\n'                                     | []                   | both("${CONFIGURATION} could not be read as YAML")
        'is not text in UTF-8'                   | [0x61, 0x3a, 0x20, 0xff, 0x0a] as byte[]               | ['--port', '18181']  | address("${CONFIGURATION} is not text in UTF-8, which is what the command reads")
    }

    void "run-app --detach asks for the address alone when the port it was given is overridden after -- by one that is no port"() {
        given:
        scriptedProject()
        new File(dir, CONFIGURATION).text = MISINDENTED

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', '18181', '--', '-Dgrails.server.port=abc')

        then:
        exit == 1
        errors() == ['| Error The port and the address the application answers on could not be determined: -Dgrails.server.port is given, and its value is not a port, ' +
                     "a number from 1 to 65535; ${CONFIGURATION} is not valid YAML at line 3, column 3. ${STARTS}give the address with --host, or ${FOREGROUND}".toString()]
        !new File(dir, 'invoked.txt').exists()
    }

    void "run-app --detach asks for nothing that was given, and says only that it can stay with the application, when what was given is overridden"() {
        given:
        scriptedProject()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', '18181', '--', '-Dgrails.server.port=${PORT}')

        then:
        exit == 1
        errors() == ['| Error The port the application answers on could not be determined: -Dgrails.server.port is given, and its value is not a port, ' +
                     "a number from 1 to 65535. ${STARTS}${FOREGROUND}".toString()]
    }

    void "run-app --detach reads nothing of an application.yml it cannot read when the port and the address are given, and starts the application"() {
        given:
        scriptedProject()
        new File(dir, CONFIGURATION).text = MISINDENTED
        int port = freePort()

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--ready-timeout', SpecWaits.boundSeconds as String, '--port', port as String, '--host', '127.0.0.1')

        then:
        exit == 0
        errors() == []
        out.toString().contains("| Application answers on port ${port}")
        record().port == port

        cleanup:
        ForgeCli.execute(beanContext, 'stop-app')
    }

    void "an application.yml that cannot be read is said to be so in the words of the command, also when it is no file the command reads"() {
        given:
        scriptedProject()
        File file = new File(dir, CONFIGURATION)
        file.text = 'server:\n  port: 1\n'
        file.setReadable(false, false)

        when:
        int exit = ForgeCli.execute(beanContext, 'run-app', '--detach', '--port', '18181')

        then:
        exit == 1
        errors() == ["| Error The address the application answers on could not be determined: ${CONFIGURATION} could not be read. ${STARTS}give the address with --host, or ${FOREGROUND}".toString()]

        cleanup:
        file.setReadable(true, false)
    }

    private static String both(String reason) {
        return "The port and the address the application answers on could not be determined: ${reason}. ${STARTS}give the port with --port and the address with --host, or ${FOREGROUND}".toString()
    }

    private static String address(String reason) {
        return "The address the application answers on could not be determined: ${reason}. ${STARTS}give the address with --host, or ${FOREGROUND}".toString()
    }

    private void scriptedProject() {
        new File(dir, 'grails-app/conf').mkdirs()
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
