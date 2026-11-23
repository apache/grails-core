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
import java.nio.file.attribute.PosixFilePermissions
import java.util.function.Consumer
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

/**
 * A build that is left running, as {@code run-app --detach} starts it, is not sent the interrupt of
 * the terminal the command line runs in, or ignores it: what {@link GradleRunner#start} does for that
 * with each shell {@code /bin/sh} can be and with and without the program {@code setsid}, whatever the
 * computer the specification runs on has. The Gradle wrapper is a script that waits.
 *
 * <p>{@code /bin/sh} is bash on macOS, which grants job control without a terminal, and dash on Debian
 * and Ubuntu, which does not; Linux has {@code setsid} and macOS has not. So each feature names its
 * shell, and gives it a {@code PATH} that has a {@code setsid} or has none: an empty directory, or one
 * with a program of that name that does what the one of Linux does.</p>
 */
@IgnoreIf({ os.windows })
class DetachedBuildSpec extends Specification {

    static final String BASH = '/bin/bash'
    static final String DASH = '/bin/dash'
    static final String PERL = '/usr/bin/perl'
    // setsid of util-linux, for a process that leads no group: a session of its own, and then the command in its place
    static final String SETSID = "#!/bin/sh\nexec ${PERL} -MPOSIX -e 'setsid() != -1 or die \"setsid: \$!\"; exec { \$ARGV[0] } @ARGV or die \"exec: \$!\"' \"\$@\"\n"

    File dir = Files.createTempDirectory('grailsdetached').toFile()
    File path = Files.createTempDirectory('grailsdetachedpath').toFile()
    List<String> failures = []
    ProcessHandle started

    void setup() {
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'settings.gradle').text = "rootProject.name = 'plain'\n"
        File wrapper = new File(dir, 'gradlew')
        // what it is given is written when it has begun, and then it waits, as a build does
        wrapper.text = '#!/bin/sh\nprintf "<%s>" "$@" > started.txt\necho >> started.txt\nexec /bin/sleep 120\n'
        Files.setPosixFilePermissions(wrapper.toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
    }

    void cleanup() {
        GradleRunner.setShell(null, null)
        if (started != null) {
            started.destroyForcibly()
        }
        dir.deleteDir()
        path.deleteDir()
    }

    @Requires({ new File(DetachedBuildSpec.BASH).canExecute() })
    void "with a shell that grants job control without a terminal, bash, and no setsid, the build is in a process group of its own"() {
        when:
        start(BASH, false)

        then:
        processGroup(started.pid()) == started.pid() as String
        processGroup(started.pid()) != processGroup(ProcessHandle.current().pid())
        new File(dir, 'started.txt').text == '<:bootRun><-Dgrails.env=development>\n'
    }

    @Requires({ new File(DetachedBuildSpec.PERL).canExecute() })
    @Unroll
    void "with setsid the build is in a session and a process group of its own, whatever the shell: #shell"() {
        when:
        start(shell, true)

        then: 'the process that was started is the build, and no other was started for it'
        started.info().commandLine().orElse('gradlew').contains('gradlew') || started.info().command().orElse('').endsWith('sleep')
        processGroup(started.pid()) == started.pid() as String
        processGroup(started.pid()) != processGroup(ProcessHandle.current().pid())
        new File(dir, 'started.txt').text == '<:bootRun><-Dgrails.env=development>\n'

        where:
        shell << [BASH, DASH, '/bin/sh'].findAll { String each -> new File(each).canExecute() }
    }

    @Requires({ new File(DetachedBuildSpec.DASH).canExecute() })
    void "with a shell that grants no job control without a terminal, dash, and no setsid, the build is in the process group of the command line and ignores the interrupt"() {
        when:
        start(DASH, false)

        then: 'which is what the sh of Debian and Ubuntu did before setsid was asked for, and why it is'
        processGroup(started.pid()) == processGroup(ProcessHandle.current().pid())

        when: 'it is sent what the terminal sends the processes of its group'
        new ProcessBuilder('kill', '-INT', started.pid() as String).start().waitFor()
        Thread.sleep(1000)

        then:
        started.alive

        when: 'and what ends a process'
        started.destroy()

        then:
        new PollingConditions(timeout: 60, delay: 0.05).eventually {
            assert !started.alive
        }
    }

    private void start(String shell, boolean setsid) {
        if (setsid) {
            File program = new File(path, 'setsid')
            program.text = SETSID
            Files.setPosixFilePermissions(program.toPath(), PosixFilePermissions.fromString('rwxr-xr-x'))
        }
        GradleRunner.setShell(shell, path.canonicalPath)
        GradleRunner runner = GradleBuild.leftRunning({ String message -> failures.add(message) } as Consumer<String>, false)
        started = runner.start(ConsoleProject.find(dir.toPath()), [':bootRun', '-Dgrails.env=development'], new File(dir, 'build.log').toPath())
        assert failures == []
        assert started != null
        // the script has begun, so the shell that started it, and setsid, have become it
        new PollingConditions(timeout: 30, delay: 0.05).eventually {
            assert new File(dir, 'started.txt').isFile() && new File(dir, 'started.txt').text.endsWith('\n')
        }
        assert started.alive
    }

    private static String processGroup(long pid) {
        Process ps = new ProcessBuilder('ps', '-o', 'pgid=', '-p', pid as String).redirectErrorStream(true).start()
        String group = ps.inputStream.text.trim()
        ps.waitFor()
        assert group ==~ /[0-9]+/
        return group
    }
}
