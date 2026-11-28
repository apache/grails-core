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
package grails.ui.shell

import java.util.concurrent.TimeUnit

import spock.lang.Specification
import spock.lang.TempDir

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import grails.ui.StartedJvmCoverage
import org.apache.grails.gradle.model.cli.JvmOptions

/**
 * {@link GrailsShell} as the {@code shell} task of a build starts it, in a JVM of its own: a shell
 * that cannot be started is a failure of that JVM, in whichever thread {@code main} runs.
 */
class GrailsShellForkSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    @TempDir
    File dir

    def "an application that cannot be started ends the JVM with a failure, #where"() {
        when:
        Map result = run(launcher, FailingShellApplication.name)

        then:
        result.ended
        result.output.contains('shell: The shell could not be started: ')
        result.output.contains('this application fails on purpose')
        result.exit == 1

        where:
        where                                                         | launcher
        'in the main thread'                                          | GrailsShell.name
        'in another thread, as with the developer tools of Spring Boot' | OtherThreadShellLauncher.name
    }

    def "an application class that is not there, and none at all, end the JVM with a failure"() {
        when:
        Map missing = run(GrailsShell.name, 'no.such.Application')
        Map none = run(GrailsShell.name)

        then:
        missing.ended
        missing.output.contains('shell: The shell could not be started: java.lang.ClassNotFoundException: no.such.Application')
        missing.exit == 1
        none.ended
        none.output.contains('Missing application class name argument')
        none.exit == 1
    }

    def "what ends the thread as the developer tools of Spring Boot end it, to start again in a thread of theirs, is left to them"() {
        when:
        Map result = run(GrailsShell.name, RestartingShellApplication.name)

        then: 'it is not said to be a shell that could not be started, and the JVM is not ended for it: what was thrown is thrown on'
        result.ended
        !result.output.contains('shell: The shell could not be started')
        result.output.contains(ShellSpecSilentExitException.name)
    }

    private Map run(String mainClass, String... arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', System.getProperty('java.class.path'),
                                '-Dgrails.env=development', "-Dbase.dir=${dir.path}".toString(), mainClass]
        command.addAll(1, StartedJvmCoverage.jvmArguments())
        // the options the Grails Gradle plugin gives the task that starts this JVM
        command.addAll(1, JvmOptions.of(Runtime.version().feature()))
        command.addAll(arguments.toList())
        File output = new File(dir, 'output.txt')
        // nothing to read: a shell that did start would end at once
        Process process = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).redirectOutput(output)
                .redirectInput(ProcessBuilder.Redirect.from(new File(dir, 'empty.txt').tap { it.text = '' })).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        [ended: ended, exit: ended ? process.exitValue() : null, output: output.text]
    }
}

@Configuration
class FailingShellApplication {

    @Bean
    String failing() {
        throw new IllegalStateException('this application fails on purpose')
    }
}

/** Calls the main method of the shell in a thread that is not the main one, and ends the main thread. */
class OtherThreadShellLauncher {

    static void main(String[] args) {
        new Thread({ GrailsShell.main(args) }, 'restartedMain').start()
    }
}

@Configuration
class RestartingShellApplication {

    @Bean
    String restarting() {
        throw new ShellSpecSilentExitException()
    }
}

/** Named as the exception of the developer tools is, which is how it is known: they are not on the class path here. */
class ShellSpecSilentExitException extends RuntimeException {
}
